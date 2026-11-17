# Prior-art audit for CB-38

Reviewed 2026-11-17 before new numerical fitting. Public source was read; no
third-party application, asset or code was installed, executed or adopted. Search
queries included `github Miniclip carrom pool physics calibration friction measured
power` and `github carrom pool aim assist measured velocity drag`. This focused
search did not establish a validated drop-in Nothing Phone (2a) profile; it does
not establish that none exists.

| Primary source | Inspected evidence and limitations | Applicability |
| --- | --- | --- |
| [BANUJA-Dissanayake](https://github.com/BANUJA-Dissanayake/carrom-pool-Ai-M-assist), `shot_calculator.py` blob `5073083360addf6dd46450d73e507bf6bc371997` | Emulator screen geometry: ideal ray reflections, hardcoded inset/radius ratio, arbitrary deflection-ray length. No measured time, drag or power fit in this calculator. HSV calibration is appearance tuning. README performance claims were not replicated. GitHub license metadata null. | Geometric reference; existing swept-circle/rail code already covers that mechanism. Does not resolve recording timing or stopping mismatch. No source adoption without licensing resolution. |
| [SaiRaheem](https://github.com/SaiRaheem/my-carrom-aim), `PhysicsEngine.kt` blob `3ce1e5bb3bcb239d0e4c1c2aac83e5998294f0f0` | Android engine advances fixed spatial increments and ideal reflections, with finite iteration/bounce caps. No measured drag, elapsed-time dynamics or power fit in inspected engine. GitHub license metadata null. | Confirms geometry-only approaches exist, but their lines cannot validate where/when bodies stop. No code adopted. |
| [ketankhandare8-hash](https://github.com/ketankhandare8-hash/carrom-pool-assistant) | Prior Jira audit inspected browser demo/configuration, including an accuracy setting. No independent phone trajectory validation established. License/build compatibility unverified. | Marketing/configuration values cannot become calibration evidence. |
| [Miniclip support](https://support.miniclip.com/hc/en-us/articles/360011901674-Strikers-Powers-Pucks-and-Trails-Carrom) | Official equipment semantics, updated 2024-05-22: Force affects shot strength/speed; equipped Powers add stats; Aim changes guideline length; pucks/trails are cosmetic. | Record effective equipment stats and distinguish aim guideline from pullback graphic. This source supplies no force-to-speed equation. Existing equipment remains unknown. |
| [Original BitAIM+ researcher](https://chocapikk.com/posts/2026/bitaim-carrom-pool-cheat-reverse-engineering/) | Author's reverse-engineering report, 2026-09-05, describes template subtraction and lookup-table behavior. Unreplicated here; neither a measured Miniclip model nor a compatible phone profile. | High-level hypothesis: separate appearance references from dynamics tables and scope by build. No proprietary/encrypted assets, hooks, modified game or payment bypass used. Article is not a license to reuse assets. |
| [Carrom contact-count challenge](https://github.com/Sri-Karthik-Avala/Carrom-Striker-Contact-Count-Prediction) | README describes deterministic rendered 600px scenes, six coins, and contact-count sensitivity to recovered positions/aim. No target phone/build or measured game recordings established. GitHub license metadata null. Reported benchmark numbers not replicated. | Useful reminder that scene error changes contact outcomes. Its synthetic benchmark cannot establish Miniclip dynamics or serve as independent real-game validation. No dataset/code adopted. |

The useful verified equipment constraint changes the acquisition metadata below.
Geometric rays already exist in our architecture; copying those projects would not
answer CB-38's timing-versus-dynamics question. We therefore compare the existing
constant model with two compact, independently implemented offline alternatives:
one speed-loss term or one monotone proxy exponent. No external numerical constants
are transferred. All remain hypotheses subject to internal shot cross-validation,
measurement sensitivity and later untouched phone validation.
