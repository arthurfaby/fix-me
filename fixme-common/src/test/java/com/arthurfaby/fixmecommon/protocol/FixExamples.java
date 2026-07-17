package com.arthurfaby.fixmecommon.protocol;

import java.nio.charset.StandardCharsets;

/**
 * Les 4 exemples de la table 2.7 du PLAN, encodés en bytes. Les checksums ont été
 * calculés indépendamment du code Java (script Python, somme des octets modulo 256),
 * jamais via Checksum.compute() : sinon les tests ne feraient que vérifier que le
 * code est d'accord avec lui-même.
 */
final class FixExamples {
    private FixExamples() {}

    static final byte[] BUY = wire(
            "49=100001", "56=100002", "35=D", "11=1",
            "55=AAPL", "54=1", "38=100", "44=150.50", "10=251");

    static final byte[] EXECUTED = wire(
            "49=100002", "56=100001", "35=8", "11=1",
            "55=AAPL", "38=100", "44=150.50", "39=2", "10=243");

    static final byte[] REJECTED = wire(
            "49=100002", "56=100001", "35=8", "11=1",
            "55=AAPL", "38=100", "39=8", "58=Not enough quantity", "10=075");

    static final byte[] LOGON = wire(
            "49=000000", "56=100001", "35=A", "10=125");

    static byte[] wire(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (String field : fields) {
            sb.append(field).append((char) FixConstants.SOH);
        }
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
