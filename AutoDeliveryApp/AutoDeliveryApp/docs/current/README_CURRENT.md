# README — CURRENT (Context Rebuild Version)
> Generated: 2026-07-02 | This is the project reference README for docs/current/

See root [README.md](../../README.md) for quick start and build instructions.

This document serves as the human-readable index for the context rebuild documents in `docs/current/`.

---

## Context Rebuild Summary

All documents in `docs/current/` were generated on **2026-07-02** from:
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/autodeliveryapp/**/*.java`
- `app/src/main/res/**`
- `app/build.gradle.kts`
- `settings.gradle.kts`
- `build.gradle.kts`

**NO archived docs were used as source of truth.** All content was derived from actual source code.

---

## docs/current/ Index

| File | Purpose |
|------|---------|
| `PROJECT_CONTEXT_CURRENT.md` | What, who, why, tech stack, module status |
| `APP_ARCHITECTURE_CURRENT.md` | Package structure, navigation diagram, data flow, dependencies |
| `FEATURES_AND_FLOWS_CURRENT.md` | 18 features with flows + 8 Mermaid diagrams |
| `FIREBASE_SCHEMA_CURRENT.md` | 6 RTDB nodes, all fields, writers, readers, security risks |
| `MQTT_CONTRACT_CURRENT.md` | Topics, payloads, status machine, activeLeg, BLE token in MQTT |
| `BLE_CONTRACT_CURRENT.md` | UUIDs, token, sender/receiver flows, robot firmware requirements |
| `TRACKING_ROUTE_CONTEXT_CURRENT.md` | C/A/B model, route lifecycle, OSRM/Haversine, MQTT sim guide |
| `MANUAL_TEST_PLAN_CURRENT.md` | 15 test cases, pre-test checklist, regression checklist |
| `KNOWN_ISSUES_CURRENT.md` | 11 issues with severity, impact, evidence from source, next action |
| `PROJECT_STATUS_CURRENT.md` | Build config, feature status table, dependency list, next steps |
| `README_CURRENT.md` | This file |

## docs/generated/ Index

| File | Purpose |
|------|---------|
| `PROJECT_TREE_CURRENT.txt` | Current file tree (excluding build/cache) |
| `PROJECT_FILES_CURRENT.csv` | All files: path, extension, type, purpose, is_source, should_keep |
| `SOURCE_CLASS_INDEX_CURRENT.md` | All 41 Java classes: path, role, group, key deps |
| `RESOURCE_INDEX_CURRENT.md` | All 37 res files indexed by category with feature mapping |

## docs/cleanup/ Index

| File | Purpose |
|------|---------|
| `PRE_CLEANUP_FILE_INVENTORY.md` | File count before cleanup (baseline audit) |
| `CLEANUP_MOVE_REPORT.md` | All moved files record |

## docs/archive/ (NOT source of truth)

Contains all files moved during cleanup:
- `audits/` — Raw audit grep outputs
- `context/` — Old project tree/CSV
- `logs/` — Build logs (67 files)
- `plans/` — Old feature plans
- `raw/` — Firebase rules sample + old README
- `reports/` — Old feature/integration reports

**Do not use these files to understand current code behavior.**
