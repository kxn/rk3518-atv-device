# RK3518 device support

Used by [rk3518-atv](https://github.com/kxn/rk3518-atv).
Do not clone this repository inside the AOSP build tree. The main bootstrap
copies overlays to their intended paths. configs/ records the actual board
kernel and U-Boot settings. Upstream notices remain applicable; see NOTICE.
The standalone BoxRemoteSetup implementation is Apache-2.0.

As of source-preview-v7.2 this repository contains only the project application,
board configuration and minimal overlays. AIC HAL, librga and the Soong plugin
are fetched/materialized from pinned upstreams by the main project, with changes
kept as patches there. Historical commits are retained for provenance.
