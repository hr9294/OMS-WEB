# Agent Instructions — OMS-WEB

## Layout
- `OMS_WEB_Python/` — Python 3.11 + Selenium automation for the OMS-WEB trading platform. **Main project.**
- `OMS WEB/` — legacy Java (Eclipse) sources for the web app.
- `background_tasks/`, `.github/`, `.kilo/` — tooling and upgrade hooks.

`D:\Anitgravity\OMS_WEB_Python` is a **directory junction** to `D:\Anitgravity\OMS-WEB\OMS_WEB_Python`.
They are the same files: edit either path, and never copy files between them.

## Environment
- Windows 11, Python 3.11.9, Git 2.46.
- Selenium flows require a live OMS instance, valid credentials and Chrome/ChromeDriver.

## Commands
Compile check — always run before committing:
```
python -m compileall OMS_WEB_Python
```

Run interactively:
```
cd OMS_WEB_Python
python main.py
```

Modes:
```
python main.py --mode multi-order [--headless]
python main.py --mode load-test
```

Dependencies:
```
pip install -r OMS_WEB_Python/requirements.txt
```

## Testing
- There is **no automated test suite**. "Tested" means the compile check passes *and*, when a live
  OMS is reachable, the relevant mode starts and logs in without a traceback.
- Never report a UI or order-execution flow as passing unless it actually ran against the OMS.

## Git
- Conventional commits: `feat|fix|test|docs|chore(scope): message`.
- Never commit `__pycache__/`, `*.pyc`, `.venv/`, logs or credentials.
- Never force-push, rewrite published history, or edit `.git/config`.
- Use a branch for risky or multi-file changes; small housekeeping commits may go to `main`.
- Check `git status` first and leave unrelated local modifications alone.
- Pull before pushing when the remote has moved (`git pull --no-rebase`).

## Secrets
No credentials, PATs, tokens or session cookies belong in the repository. OMS login input is
supplied at runtime, not stored in code.
