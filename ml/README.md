# Machine learning workspace

This directory is reserved for model development metadata, evaluation scripts, and documented conversion workflows.

Do not commit large raw datasets, personal videos, or model weights until provenance, consent, and license terms have been reviewed. Prefer external dataset/model storage with versioned manifests.

Every model release must record:
- architecture and source repository
- exact code commit and model artifact checksum
- training/validation dataset provenance and license
- model-weight license and redistribution terms
- input size, preprocessing, output schema
- device benchmark, latency, memory, and accuracy metrics
- known failure cases and intended-use limitations
