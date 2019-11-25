#!/bin/sh

KEYPASS=MyStorePassword
STOREPASS=MyStorePassword
ALIAS=MyKeyAlias

rm -f testkeystore

keytool -keystore testkeystore -storetype pkcs12 -genkey -dname 'CN=localhost, O=R3, L=London, C=UK' -keyalg RSA -alias ${ALIAS} -validity 3650 -keypass ${KEYPASS} -storepass ${STOREPASS}
