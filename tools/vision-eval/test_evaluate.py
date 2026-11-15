import copy
import hashlib
import json
from pathlib import Path
import unittest

from evaluate import evaluate


class EvaluationTest(unittest.TestCase):
    def setUp(self):
        self.fixture = {
            "version": 1, "dataset": "unit", "frames": [
                {"id": "objects", "mode": "objects", "objects": [
                    {"kind": "QUEEN", "x": 10, "y": 20, "radius": 5}]},
                {"id": "board", "mode": "board", "corners": [[0, 0], [100, 0], [100, 100], [0, 100]],
                 "width_mm": 740, "height_mm": 740}]
        }
        self.predictions = {"version": 1, "dataset": "unit", "frames": [
            {"id": "objects", "status": "observed", "objects": [
                {"kind": "QUEEN", "x": 11, "y": 20, "radius": 5.5}]},
            {"id": "board", "status": "found", "corners": [[0, 0], [100, 0], [100, 100], [0, 100]]}]}

    def test_metrics_and_scale(self):
        report = evaluate(self.fixture, self.predictions)
        queen = report["classes"]["QUEEN"]
        self.assertEqual((1, 0, 0), (queen["tp"], queen["fp"], queen["fn"]))
        self.assertEqual(1, queen["center_mean_mm"])
        self.assertEqual(.5, queen["radius_mean_mm"])
        self.assertEqual(7.4, report["boards"]["calibration"][0]["top_mm_per_pixel"])

    def test_duplicate_and_wrong_class_are_false_positives(self):
        objects = self.predictions["frames"][0]["objects"]
        objects.append(copy.deepcopy(objects[0]))
        objects.append(dict(objects[0], kind="STRIKER"))
        report = evaluate(self.fixture, self.predictions)
        self.assertEqual(1, report["classes"]["QUEEN"]["fp"])
        self.assertEqual(1, report["classes"]["STRIKER"]["fp"])

    def test_failed_detection_retains_false_negatives(self):
        self.predictions["frames"][0] = {"id": "objects", "status": "failed", "reason": "INVALID_FRAME"}
        self.predictions["frames"][1] = {"id": "board", "status": "failed", "reason": "BOARD_NOT_FOUND"}
        report = evaluate(self.fixture, self.predictions)
        self.assertEqual(1, report["classes"]["QUEEN"]["fn"])
        self.assertEqual(1, report["boards"]["fn"])
        self.assertEqual(2, report["failed_frames"])

    def test_incomplete_duplicate_unknown_and_nonfinite_inputs_fail(self):
        for mutation in ("missing", "duplicate", "nan", "unknown", "dataset"):
            with self.subTest(mutation=mutation):
                predictions = copy.deepcopy(self.predictions)
                if mutation == "missing":
                    predictions["frames"].pop()
                elif mutation == "duplicate":
                    predictions["frames"].append(predictions["frames"][0])
                elif mutation == "nan":
                    predictions["frames"][0]["objects"][0]["x"] = float("nan")
                elif mutation == "unknown":
                    predictions["frames"][0]["objects"][0]["kind"] = "UNKNOWN"
                else:
                    predictions["dataset"] = "other"
                with self.assertRaises(ValueError):
                    evaluate(self.fixture, predictions)

    def test_matching_maximizes_cardinality_in_crowded_scene(self):
        self.fixture["frames"][0]["objects"].append({"kind": "QUEEN", "x": 13, "y": 20, "radius": 5})
        self.predictions["frames"][0]["objects"] = [
            {"kind": "QUEEN", "x": 11.5, "y": 20, "radius": 5},
            {"kind": "QUEEN", "x": 9, "y": 20, "radius": 5}]
        self.assertEqual(2, evaluate(self.fixture, self.predictions)["classes"]["QUEEN"]["tp"])

    def test_bad_board_counts_fp_fn_and_rejected_negative_is_valid(self):
        self.predictions["frames"][1]["corners"] = [[10, 0], [110, 0], [110, 100], [10, 100]]
        report = evaluate(self.fixture, self.predictions)
        self.assertEqual((0, 1, 1), tuple(report["boards"][x] for x in ("tp", "fp", "fn")))
        self.assertEqual(10, report["boards"]["corner_max_px"])
        self.fixture["frames"][1]["corners"] = None
        self.predictions["frames"][1] = {"id": "board", "status": "failed", "reason": "BOARD_NOT_FOUND"}
        self.assertEqual(0, evaluate(self.fixture, self.predictions)["boards"]["fn"])

    def test_invalid_geometry_and_gates_fail(self):
        for value in (0, -1, float("inf"), True):
            with self.assertRaises(ValueError):
                evaluate(self.fixture, self.predictions, center_gate=value)
        self.predictions["frames"][1]["corners"] = [[0, 0]] * 4
        with self.assertRaises(ValueError):
            evaluate(self.fixture, self.predictions)

    def test_order_and_zero_denominators(self):
        original = evaluate(self.fixture, self.predictions)
        self.predictions["frames"].reverse()
        self.assertEqual(original, evaluate(self.fixture, self.predictions))
        self.assertIsNone(original["classes"]["POCKET"]["precision"])
        self.assertIsNone(original["classes"]["POCKET"]["recall"])


class DatasetGoldenTest(unittest.TestCase):
    def test_actual_device_snapshot_matches_golden_report_and_asset_hashes(self):
        root = Path(__file__).resolve().parents[2]
        assets = root / "core/vision/src/androidTest/assets"
        dataset = root / "test-fixtures/vision"
        manifest = json.loads((assets / "vision-v1.json").read_text())
        predictions = json.loads((dataset / "sample-predictions.json").read_text())
        for frame in manifest["frames"]:
            self.assertEqual(frame["sha256"], hashlib.sha256((assets / frame["asset"]).read_bytes()).hexdigest())
        report = evaluate(manifest, predictions)
        self.assertEqual(json.loads((dataset / "sample-report.json").read_text()), report)
        self.assertEqual(8, report["boards"]["tp"])
        self.assertEqual(0, report["boards"]["fp"])
        self.assertEqual(0, report["boards"]["fn"])
        self.assertEqual(27, sum(x["tp"] for x in report["classes"].values()))
        self.assertEqual(0, sum(x["fp"] + x["fn"] for x in report["classes"].values()))
        predictions["frames"][0]["sha256"] = "stale"
        with self.assertRaises(ValueError):
            evaluate(manifest, predictions)


if __name__ == "__main__":
    unittest.main()
