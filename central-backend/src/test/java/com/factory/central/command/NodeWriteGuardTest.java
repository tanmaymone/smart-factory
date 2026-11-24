package com.factory.central.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factory.central.registry.TagDataType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@Tag("GW-NODE")
class NodeWriteGuardTest {

    @Test
    void allowsMatchingFloatAndUnknownFacts() {
        assertThatCode(() -> NodeWriteGuard.check(TagDataType.FLOAT, "FLOAT", true))
                .doesNotThrowAnyException();
        assertThatCode(() -> NodeWriteGuard.check(TagDataType.FLOAT, null, null))
                .doesNotThrowAnyException();
        assertThat(NodeWriteGuard.mismatch(TagDataType.FLOAT, "float")).isNull();
    }

    @Test
    void rejectsFloatOnIntegerNode() {
        assertThatThrownBy(() -> NodeWriteGuard.check(TagDataType.FLOAT, "INT", true))
                .isInstanceOf(CommandException.class)
                .satisfies(ex -> {
                    CommandException error = (CommandException) ex;
                    assertThat(error.getCode()).isEqualTo(CommandErrorCode.NODE_TYPE_MISMATCH);
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(error.getMessage()).isEqualTo("FLOAT requires Float or Double node datatype");
                });
    }

    @Test
    void rejectsOtherRegistryFamilies() {
        assertThat(NodeWriteGuard.mismatch(TagDataType.INT, "FLOAT"))
                .isEqualTo("INT requires an integer node datatype");
        assertThat(NodeWriteGuard.mismatch(TagDataType.BOOLEAN, "INT"))
                .isEqualTo("BOOLEAN requires Boolean node datatype");
        assertThat(NodeWriteGuard.mismatch(TagDataType.STRING, "FLOAT"))
                .isEqualTo("STRING requires String node datatype");
    }

    @Test
    void rejectsReadOnlyNodeAfterTypeMatches() {
        assertThatThrownBy(() -> NodeWriteGuard.check(TagDataType.FLOAT, "FLOAT", false))
                .isInstanceOf(CommandException.class)
                .satisfies(ex -> assertThat(((CommandException) ex).getCode())
                        .isEqualTo(CommandErrorCode.NODE_NOT_WRITABLE));
    }
}
