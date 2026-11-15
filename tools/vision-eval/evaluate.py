"""Offline scoring of version-1 vision predictions. Python standard library only."""
import argparse
import json
import math
from pathlib import Path

KINDS = ("POCKET", "BLACK_DISC", "WHITE_DISC", "QUEEN", "STRIKER", "OBSTACLE")


def number(value):
    if isinstance(value, bool) or not isinstance(value, (float, int)) or not math.isfinite(value):
        raise ValueError("Expected finite number")
    return value


def objects(items):
    for item in items:
        if item["kind"] not in KINDS:
            raise ValueError("Unknown object kind")
        for field in ("x", "y", "radius"):
            number(item[field])
        if item["radius"] <= 0:
            raise ValueError("Radius must be positive")
    return items


def corners(points):
    if len(points) != 4 or any(len(p) != 2 for p in points):
        raise ValueError("Expected four ordered pixel corners")
    for point in points:
        for value in point:
            number(value)
    # Positive cross products enforce convex clockwise screen coordinates.
    for i in range(4):
        a, b, c = (points[(i + j) % 4] for j in range(3))
        if (b[0]-a[0])*(c[1]-b[1]) - (b[1]-a[1])*(c[0]-b[0]) <= 0:
            raise ValueError("Corners must be convex and clockwise")
    return points


def index(document):
    if document["version"] != 1:
        raise ValueError("Unsupported format version")
    frames = {frame["id"]: frame for frame in document["frames"]}
    if not frames or len(frames) != len(document["frames"]):
        raise ValueError("Empty dataset or duplicate frame ID")
    return frames


def match(expected, predicted, center_gate, radius_gate):
    """Deterministic maximum-cardinality bipartite matching, with distance tie order."""
    edges = []
    for truth in expected:
        candidates = []
        for j, prediction in enumerate(predicted):
            distance = math.hypot(truth["x"]-prediction["x"], truth["y"]-prediction["y"])
            if distance <= center_gate and abs(truth["radius"]-prediction["radius"]) <= radius_gate:
                candidates.append((distance, j))
        edges.append([j for _, j in sorted(candidates)])
    assigned = {}

    def augment(i, seen):
        for j in edges[i]:
            if j not in seen:
                seen.add(j)
                if j not in assigned or augment(assigned[j], seen):
                    assigned[j] = i
                    return True
        return False

    for i in range(len(expected)):
        augment(i, set())
    return sorted((i, j) for j, i in assigned.items())


def mean(values):
    return sum(values) / len(values) if values else None


def evaluate(fixture, predictions, center_gate=2.0, radius_gate=2.0, corner_gate=5.0):
    for gate in (center_gate, radius_gate, corner_gate):
        if number(gate) <= 0:
            raise ValueError("Gates must be positive")
    truth, actual = index(fixture), index(predictions)
    if fixture["dataset"] != predictions["dataset"] or truth.keys() != actual.keys():
        raise ValueError("Dataset and frame IDs must match exactly; missing output is not empty detection")
    classes = {kind: {"tp": 0, "fp": 0, "fn": 0, "centers": [], "radii": []} for kind in KINDS}
    board = {"tp": 0, "fp": 0, "fn": 0, "corner_mean_px": None, "corner_max_px": None, "calibration": []}
    errors, failed = [], 0
    for key in sorted(truth):
        frame, prediction = truth[key], actual[key]
        if "sha256" in frame and prediction.get("sha256") != frame["sha256"]:
            raise ValueError("Prediction asset hash does not match fixture")
        status = prediction["status"]
        if status == "failed":
            if not prediction.get("reason") or "objects" in prediction or "corners" in prediction:
                raise ValueError("Failure must have a reason and no geometry")
            failed += 1
        if frame["mode"] == "objects":
            if status not in ("observed", "failed"):
                raise ValueError("Invalid object status")
            expected = objects(frame["objects"])
            observed = objects(prediction["objects"]) if status == "observed" else []
            for kind, metrics in classes.items():
                e = sorted((x for x in expected if x["kind"] == kind), key=lambda x: (x["x"], x["y"], x["radius"]))
                p = sorted((x for x in observed if x["kind"] == kind), key=lambda x: (x["x"], x["y"], x["radius"]))
                pairs = match(e, p, center_gate, radius_gate)
                metrics["tp"] += len(pairs)
                metrics["fn"] += len(e)-len(pairs)
                metrics["fp"] += len(p)-len(pairs)
                for i, j in pairs:
                    metrics["centers"].append(math.hypot(e[i]["x"]-p[j]["x"], e[i]["y"]-p[j]["y"]))
                    metrics["radii"].append(abs(e[i]["radius"]-p[j]["radius"]))
        elif frame["mode"] == "board":
            if status not in ("found", "failed"):
                raise ValueError("Invalid board status")
            expected = corners(frame["corners"]) if frame["corners"] is not None else None
            observed = corners(prediction["corners"]) if status == "found" else None
            distances = [math.dist(a, b) for a, b in zip(expected, observed)] if expected and observed else []
            good = bool(distances) and max(distances) <= corner_gate
            board["tp"] += int(good)
            board["fn"] += int(expected is not None and not good)
            board["fp"] += int(observed is not None and not good)
            errors.extend(distances)
            if observed:
                width, height = number(frame["width_mm"]), number(frame["height_mm"])
                if min(width, height) <= 0:
                    raise ValueError("Physical dimensions must be positive")
                calibration = {"id": key}
                for i, (edge, mm) in enumerate(zip(("top", "right", "bottom", "left"), (width, height, width, height))):
                    calibration[edge+"_mm_per_pixel"] = mm / math.dist(observed[i], observed[(i+1) % 4])
                board["calibration"].append(calibration)
        else:
            raise ValueError("Unknown fixture mode")
    for metrics in classes.values():
        tp, fp, fn = (metrics[x] for x in ("tp", "fp", "fn"))
        metrics["precision"] = tp/(tp+fp) if tp+fp else None
        metrics["recall"] = tp/(tp+fn) if tp+fn else None
        metrics["center_mean_mm"] = mean(metrics.pop("centers"))
        metrics["radius_mean_mm"] = mean(metrics.pop("radii"))
    board["corner_mean_px"], board["corner_max_px"] = mean(errors), max(errors) if errors else None
    return {"version": 1, "dataset": fixture["dataset"], "gates": {"center_mm": center_gate, "radius_mm": radius_gate,
            "corner_px": corner_gate}, "failed_frames": failed, "boards": board, "classes": classes}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fixture", type=Path)
    parser.add_argument("predictions", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        report = evaluate(json.loads(args.fixture.read_text(encoding="utf-8")),
                          json.loads(args.predictions.read_text(encoding="utf-8")))
        result = json.dumps(report, indent=2, sort_keys=True, allow_nan=False) + "\n"
        if args.output:
            args.output.write_text(result, encoding="utf-8")
        else:
            print(result, end="")
    except (ValueError, KeyError, TypeError, OSError) as error:
        parser.exit(2, f"Invalid evaluation input: {error}\n")


if __name__ == "__main__":
    main()
