# Third-party notices and license register

No third-party source code, trained model, or dataset is bundled in the initial scaffold.

Before a dependency or asset is merged, add a row containing:

| Component / asset | Exact version or commit | Source URL | License | Required notices / obligations | Review status |
|---|---|---|---|---|---|
| AndroidX / Jetpack Compose | To be pinned | https://developer.android.com/jetpack | Review official artifact terms | Preserve required notices | Pending |
| CameraX | To be pinned | https://developer.android.com/media/camera/camerax | Review exact artifact metadata | Preserve required notices | Pending |
| Room | To be pinned | https://developer.android.com/training/data-storage/room | Review exact artifact metadata | Preserve required notices | Pending |
| LiteRT (future) | To be pinned | https://github.com/google-ai-edge/LiteRT | Apache-2.0 stated by upstream project; verify selected release | Include Apache-2.0 license and notices as required | Not integrated |

## Mandatory review

1. Review the exact release/tag, not only the project homepage.
2. Confirm the license applies to the code, model weights, datasets, and any downloaded assets separately.
3. Check NOTICE files, attribution, patent terms, redistribution conditions, and source-disclosure obligations.
4. Do not assume model weights share the source-code license.
5. Record reviewer, date, and evidence before shipping.
6. Re-check licenses when versions change.

This file is a review register, not legal advice.
