# Security policy

EventMesh is a portfolio project and is not currently offered as a hosted
production service.

## Reporting

Please report a suspected vulnerability privately to the repository owner
instead of opening a public issue.

## Development credentials

The repository includes a known local-development API key. It has no value
outside the local seed database. Production-like environments must provision
unique keys through a secret manager and store only their hashes.

Never commit:

- production API keys;
- webhook signing secrets;
- database credentials;
- cloud credentials;
- captured customer payloads.

The evolving security model is documented in
[docs/security-model.md](docs/security-model.md).

