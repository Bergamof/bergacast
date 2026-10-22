package fr.bergadev.bergacast.audio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ByteRangeParserTest {

    private static final long LENGTH = 1000;

    @ParameterizedTest(name = "{0} -> {1}-{2}")
    @CsvSource({
        "bytes=0-99, 0, 99",
        "bytes=100-, 100, 999",
        "bytes=900-2000, 900, 999",
        "bytes=999-999, 999, 999",
        "bytes=-100, 900, 999",
        "bytes=-5000, 0, 999",
        "' bytes=0-0 ', 0, 0"
    })
    void parsesSatisfiableRanges(String header, long first, long last) {
        ByteRange range = ByteRangeParser.parse(header, LENGTH);

        assertThat(range).isEqualTo(new ByteRange.Partial(first, last, LENGTH));
        ByteRange.Partial partial = (ByteRange.Partial) range;
        assertThat(partial.length()).isEqualTo(last - first + 1);
        assertThat(partial.contentRange()).isEqualTo("bytes " + first + "-" + last + "/" + LENGTH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bytes=1000-", "bytes=1500-2000", "bytes=-0"})
    void rejectsRangesOutsideOfTheContent(String header) {
        assertThat(ByteRangeParser.parse(header, LENGTH)).isEqualTo(new ByteRange.Unsatisfiable(LENGTH));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "bytes=",
                "bytes=-",
                "bytes=10-5",
                "bytes=0-1,5-6",
                "items=0-5",
                "bytes=a-b",
                "bytes=99999999999999999999-"
            })
    void ignoresMissingUnsupportedOrInvalidRanges(String header) {
        assertThat(ByteRangeParser.parse(header, LENGTH)).isEqualTo(new ByteRange.Full(LENGTH));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bytes=0-", "bytes=-10"})
    void cannotSatisfyAnyRangeOfAnEmptyContent(String header) {
        assertThat(ByteRangeParser.parse(header, 0)).isEqualTo(new ByteRange.Unsatisfiable(0));
    }
}
