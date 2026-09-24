EscoF 0.7.0 derives from Leaf 26.2 build 118, commit
4b38592a41f9df8fd9b656c21ac1df3f4ada9473, and its Paper upstream
e5fe71723e2ffde7cc9fafc085ac3bb73e63175e.

Original authors, patch headers and license files are retained. See LICENSE.md,
license/, and upstream source headers. Esco-specific helper code, verification
code and patches added for this release are provided under GPL-3.0-only.
The distributed binary is GPL-3.0-only as described by the upstream license.

This project is not an official release of Paper, Leaf or Mojang.

PaperProbePoiReference.java is a renamed copy of the pinned Leaf/Moonrise
implementation used as a differential test oracle. Its upstream notices and
licenses continue to apply. It is packaged only inside the verification plugin.

The Paperclip-derived launcher downloads the Minecraft server and applies its
patches at first run. Minecraft use remains subject to Mojang's EULA. No accepted
eula.txt, world data, downloaded Minecraft JAR, JDK or third-party plugin JAR is
included in the source distribution.
