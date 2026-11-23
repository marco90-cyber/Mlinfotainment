# Chiave di test di Android (pubblica)

`platform.pk8` e `platform.x509.pem` sono le chiavi di test "platform" di AOSP (android-12.0.0_r1,
`build/make/target/product/security`). Sono pubbliche. Servono solo per la versione "Sistema" di MLinfotainment,
che si installa soltanto su dispositivi (di solito autoradio e tablet cinesi) firmati con le stesse chiavi.
`platform.p12` è la stessa chiave convertita per Gradle (password `android`).
