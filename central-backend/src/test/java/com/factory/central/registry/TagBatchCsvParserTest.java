package com.factory.central.registry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-REGISTRY")
class TagBatchCsvParserTest {

    private static final String HEADER =
            "siteId,lineId,tagCode,description,protocol,endpointUrl,externalAddress,dataType,writable,deadband";

    @Test
    void stripsLeadingBom() {
        String body = "\uFEFF" + HEADER + "\nNASHIK,LINE-01,T1,desc,OPC_UA,opc.tcp://x,ns=3;i=1,FLOAT,false,0.1\n";
        TagBatchCsvParser.ParseResult result = TagBatchCsvParser.parse(body);
        assertThat(result.ok()).isTrue();
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().getFirst().get("siteId")).isEqualTo("NASHIK");
    }

    @Test
    void unescapesQuotedQuotes() {
        String body = HEADER + "\nNASHIK,LINE-01,T1,\"say \"\"hi\"\"\",OPC_UA,opc.tcp://x,ns=3;i=1,FLOAT,false,0.1\n";
        TagBatchCsvParser.ParseResult result = TagBatchCsvParser.parse(body);
        assertThat(result.ok()).isTrue();
        assertThat(result.rows().getFirst().get("description")).isEqualTo("say \"hi\"");
    }

    @Test
    void acceptsReorderedHeaders() {
        String body =
                "tagCode,siteId,lineId,description,protocol,endpointUrl,externalAddress,dataType,writable,deadband\n"
                        + "T1,NASHIK,LINE-01,desc,OPC_UA,opc.tcp://x,ns=3;i=1,FLOAT,false,0.1\n";
        TagBatchCsvParser.ParseResult result = TagBatchCsvParser.parse(body);
        assertThat(result.ok()).isTrue();
        assertThat(result.rows().getFirst().get("tagCode")).isEqualTo("T1");
    }

    @Test
    void missingRequiredHeaderIsPayloadError() {
        String body = "siteId,lineId,tagCode\nNASHIK,LINE-01,T1\n";
        TagBatchCsvParser.ParseResult result = TagBatchCsvParser.parse(body);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).anyMatch(err -> "protocol".equals(err.field()));
    }

    @Test
    void unknownHeaderIsPayloadError() {
        String body = HEADER + ",extra\nNASHIK,LINE-01,T1,desc,OPC_UA,opc.tcp://x,ns=3;i=1,FLOAT,false,0.1,x\n";
        TagBatchCsvParser.ParseResult result = TagBatchCsvParser.parse(body);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).anyMatch(err -> "extra".equals(err.field()));
    }

    @Test
    void blankTrailingLineNotCounted() {
        String body = HEADER + "\nNASHIK,LINE-01,T1,desc,OPC_UA,opc.tcp://x,ns=3;i=1,FLOAT,false,0.1\n\n";
        TagBatchCsvParser.ParseResult result = TagBatchCsvParser.parse(body);
        assertThat(result.ok()).isTrue();
        assertThat(result.rows()).hasSize(1);
    }

    @Test
    void unclosedQuoteIsPayloadError() {
        String body = HEADER + "\nNASHIK,LINE-01,T1,\"open,OPC_UA,opc.tcp://x,ns=3;i=1,FLOAT,false,0.1\n";
        TagBatchCsvParser.ParseResult result = TagBatchCsvParser.parse(body);
        assertThat(result.ok()).isFalse();
        assertThat(result.errors()).anyMatch(err -> err.message().contains("unclosed quote"));
    }

    @Test
    void extraCellFailsOnlyThatRow() {
        String body = HEADER
                + "\nNASHIK,LINE-01,T1,desc,OPC_UA,opc.tcp://x,ns=3;i=1,FLOAT,false,0.1,extra\n"
                + "NASHIK,LINE-01,T2,desc,OPC_UA,opc.tcp://x,ns=3;i=2,FLOAT,false,0.1\n";
        TagBatchCsvParser.ParseResult result = TagBatchCsvParser.parse(body);
        assertThat(result.ok()).isTrue();
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).columnCountExceeded()).isTrue();
        assertThat(result.rows().get(1).columnCountExceeded()).isFalse();
    }

    @Test
    void embeddedBreakKeepsIndexAndSourceLine() {
        String lf = HEADER
                + "\nNASHIK,LINE-01,T1,\"lineA\nlineB\",OPC_UA,opc.tcp://x,ns=3;i=1,FLOAT,false,0.1\n"
                + "\n"
                + "NASHIK,LINE-01,T2,desc,OPC_UA,opc.tcp://x,ns=3;i=2,FLOAT,false,0.1\n";
        TagBatchCsvParser.ParseResult lfResult = TagBatchCsvParser.parse(lf);
        assertThat(lfResult.ok()).isTrue();
        assertThat(lfResult.rows()).hasSize(2);
        assertThat(lfResult.rows().get(0).sourceLine()).isEqualTo(2);
        assertThat(lfResult.rows().get(0).get("description")).isEqualTo("lineA\nlineB");
        assertThat(lfResult.rows().get(1).sourceLine()).isEqualTo(5);

        String crlf = HEADER
                + "\r\nNASHIK,LINE-01,T1,\"lineA\r\nlineB\",OPC_UA,opc.tcp://x,ns=3;i=1,FLOAT,false,0.1\r\n"
                + "\r\n"
                + "NASHIK,LINE-01,T2,desc,OPC_UA,opc.tcp://x,ns=3;i=2,FLOAT,false,0.1\r\n";
        TagBatchCsvParser.ParseResult crlfResult = TagBatchCsvParser.parse(crlf);
        assertThat(crlfResult.ok()).isTrue();
        assertThat(crlfResult.rows()).hasSize(2);
        assertThat(crlfResult.rows().get(0).sourceLine()).isEqualTo(2);
        assertThat(crlfResult.rows().get(1).sourceLine()).isEqualTo(5);
    }

    @Test
    void emptyAndWhitespaceBodiesRequireHeader() {
        assertThat(TagBatchCsvParser.parse("").ok()).isFalse();
        assertThat(TagBatchCsvParser.parse("").message()).isEqualTo("CSV header is required");
        assertThat(TagBatchCsvParser.parse("   \r\n\t").ok()).isFalse();
        assertThat(TagBatchCsvParser.parse("\uFEFF  \n").ok()).isFalse();
        assertThat(TagBatchCsvParser.parse("\uFEFF  \n").message()).isEqualTo("CSV header is required");
    }

    @Test
    void headerOnlyIsEmptySuccess() {
        TagBatchCsvParser.ParseResult result = TagBatchCsvParser.parse(HEADER + "\n");
        assertThat(result.ok()).isTrue();
        assertThat(result.rows()).isEmpty();
    }
}
