# Publishing

The source folder is suitable for a public **experimental/source-preview** repository. It is not yet a complete end-user runtime distribution.

1. Run `python tools/test.py`, `python tools/audit_public.py` and build the app.
2. Use a stable release key stored outside Git. Keep it for updates.
3. Review `THIRD_PARTY.md` and preserve relevant upstream notices. Optional root-client binaries require their license and corresponding-source information with the release.
4. Keep GSI images, Google packages, proprietary vendor libraries, captures, logs, credentials and userdata out of Git and release assets.
5. Publish source and, if desired, the signed preview APK. State that it requires a prepared runtime. Do not label it as a clean-install or universal release.
6. Before an end-user release, complete and test the clean-device bootstrap described in `SETUP.md`, on a second supported phone with fresh guest data.

No GitHub remote or upload is configured by these tools. From the repository folder, create an empty repository on GitHub and use its actual URL:

```sh
git add .
git commit -m "Add gsionlocked source preview"
git remote add origin YOUR_REPOSITORY_URL
git push -u origin main
```

Keep APKs in GitHub Releases rather than committing generated APKs or multi-gigabyte GSI files.
