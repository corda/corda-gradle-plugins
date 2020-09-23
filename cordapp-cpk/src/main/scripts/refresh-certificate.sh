#!/bin/sh

# This script refreshes the certificate for the development
# CorDapp signing key in src/main/resources/certificates.

KEYSTORE=cordadevcodesign.jks
KEYPASS=cordacadevkeypass
STOREPASS=cordacadevpass
STORETYPE=jks
ALIAS=cordacodesign

REQUEST=request.csr
CERTIFICATE=certificate.pem

rm -f ${REQUEST} ${CERTIFICATE}

keytool -keystore ${KEYSTORE} \
        -storetype ${STORETYPE} \
        -certreq \
        -alias ${ALIAS} \
        -keypass ${KEYPASS} \
        -storepass ${STOREPASS} \
        -file ${REQUEST}
RC=$?
if [ $RC -ne 0 ]; then
    echo "Failed to generate certificate request: $RC"
    exit 1
fi

keytool -keystore ${KEYSTORE} \
        -storetype ${STORETYPE} \
        -gencert \
        -alias ${ALIAS} \
        -keypass ${KEYPASS} \
        -storepass ${STOREPASS} \
        -infile ${REQUEST} \
        -outfile ${CERTIFICATE} \
        -validity 3650 \
        -ext KU=DigitalSignature \
        -ext EKU=codeSigning \
        -rfc
RC=$?
if [ $RC -ne 0 ]; then
    echo "Failed to generate certificate: $RC"
    exit 1
fi

keytool -keystore ${KEYSTORE} \
        -storetype ${STORETYPE} \
        -importcert \
        -alias ${ALIAS} \
        -keypass ${KEYPASS} \
        -storepass ${STOREPASS} \
        -file ${CERTIFICATE}
RC=$?
if [ $RC -ne 0 ]; then
    echo "Failed to import new certificate: $RC"
    exit 1
fi

rm -f ${REQUEST} ${CERTIFICATE}

