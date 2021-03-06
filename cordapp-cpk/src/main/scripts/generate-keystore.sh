#!/bin/sh

# This script generates the development CorDapp signing key
# in src/main/resources/certificates.
#
# Note: It generates the keystore from scratch with a brand new key.

KEYSTORE=cordadevcodesign.p12
STOREPASS=cordacadevpass
STORETYPE=pkcs12
KEYPASS=${STOREPASS}
ALIAS=cordacodesign

rm -f ${KEYSTORE}

keytool -keystore ${KEYSTORE} \
        -storetype ${STORETYPE} \
        -genkey \
        -dname 'CN=Corda Dev Code Signer, OU=R3, O=Corda, L=London, C=GB' \
        -keyalg EC \
        -groupname secp256r1 \
        -alias ${ALIAS} \
        -validity 3650 \
        -ext KU=DigitalSignature \
        -ext EKU=codeSigning \
        -keypass ${KEYPASS} \
        -storepass ${STOREPASS} \
        -v
