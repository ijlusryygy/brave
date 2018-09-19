package brave.propagation;

import brave.internal.HexCodec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceContextTest {
  TraceContext base = TraceContext.newBuilder().traceId(1L).spanId(1L).build();

  @Test public void compareUnequalIds() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(3L).build();

    assertThat(context)
        .isNotEqualTo(TraceContext.newBuilder().traceId(333L).spanId(1L).build());
    assertThat(context.hashCode())
        .isNotEqualTo(TraceContext.newBuilder().traceId(333L).spanId(1L).build().hashCode());
  }

  @Test public void compareEqualIds() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(444L).build();

    assertThat(context)
        .isEqualTo(TraceContext.newBuilder().traceId(333L).spanId(444L).build());
    assertThat(context.hashCode())
        .isEqualTo(TraceContext.newBuilder().traceId(333L).spanId(444L).build().hashCode());
  }

  @Test public void equalOnSameTraceIdSpanId() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(444L).build();

    assertThat(context)
        .isEqualTo(context.toBuilder().parentId(1L).build());
    assertThat(context.hashCode())
        .isEqualTo(context.toBuilder().parentId(1L).build().hashCode());
  }

  @Test
  public void testToString_lo() {
    TraceContext context = TraceContext.newBuilder().traceId(333L).spanId(3).parentId(2L).build();

    assertThat(context.toString())
        .isEqualTo("000000000000014d/0000000000000003");
  }

  @Test
  public void testToString() {
    TraceContext context =
        TraceContext.newBuilder().traceIdHigh(333L).traceId(444L).spanId(3).parentId(2L).build();

    assertThat(context.toString())
        .isEqualTo("000000000000014d00000000000001bc/0000000000000003");
  }

  @Test(expected = UnsupportedOperationException.class)
  public void ensureImmutable_returnsImmutableEmptyList() {
    TraceContext.ensureImmutable(new ArrayList<>()).add("foo");
  }

  @Test public void ensureImmutable_convertsToSingletonList() {
    List<Object> list = new ArrayList<>();
    list.add("foo");
    TraceContext.ensureImmutable(list);
    assertThat(TraceContext.ensureImmutable(list).getClass().getSimpleName())
        .isEqualTo("SingletonList");
  }

  @Test public void ensureImmutable_returnsEmptyList() {
    List<Object> list = Collections.emptyList();
    assertThat(TraceContext.ensureImmutable(list))
        .isSameAs(list);
  }

  @Test public void canUsePrimitiveOverloads() {
    TraceContext primitives = base.toBuilder()
        .parentId(1L)
        .sampled(true)
        .debug(true)
        .build();

    TraceContext objects =  base.toBuilder()
        .parentId(Long.valueOf(1L))
        .sampled(Boolean.TRUE)
        .debug(Boolean.TRUE)
        .build();

    assertThat(primitives)
        .isEqualToComparingFieldByField(objects);
  }

  @Test public void nullToZero() {
    TraceContext nulls = base.toBuilder()
        .parentId(null)
        .build();

    TraceContext zeros =  base.toBuilder()
        .parentId(0L)
        .build();

    assertThat(nulls)
        .isEqualToComparingFieldByField(zeros);
  }

  @Test public void parseTraceId_128bit() {
    String traceIdString = "463ac35c9f6413ad48485a3953bb6124";

    TraceContext.Builder builder = parseGoodTraceID(traceIdString);

    assertThat(HexCodec.toLowerHex(builder.traceIdHigh))
        .isEqualTo("463ac35c9f6413ad");
    assertThat(HexCodec.toLowerHex(builder.traceId))
        .isEqualTo("48485a3953bb6124");
  }

  @Test public void parseTraceId_64bit() {
    String traceIdString = "48485a3953bb6124";

    TraceContext.Builder builder = parseGoodTraceID(traceIdString);

    assertThat(builder.traceIdHigh).isZero();
    assertThat(HexCodec.toLowerHex(builder.traceId))
        .isEqualTo(traceIdString);
  }

  @Test public void parseTraceId_short128bit() {
    String traceIdString = "3ac35c9f6413ad48485a3953bb6124";

    TraceContext.Builder builder = parseGoodTraceID(traceIdString);

    assertThat(HexCodec.toLowerHex(builder.traceIdHigh))
        .isEqualTo("003ac35c9f6413ad");
    assertThat(HexCodec.toLowerHex(builder.traceId))
        .isEqualTo("48485a3953bb6124");
  }

  @Test public void parseTraceId_short64bit() {
    String traceIdString = "6124";

    TraceContext.Builder builder = parseGoodTraceID(traceIdString);

    assertThat(builder.traceIdHigh).isZero();
    assertThat(HexCodec.toLowerHex(builder.traceId))
        .isEqualTo("000000000000" + traceIdString);
  }

  /**
   * Trace ID is a required parameter, so it cannot be null empty malformed or other nonsense.
   *
   * <p>Notably, this shouldn't throw exception or allocate anything
   */
  @Test public void parseTraceId_malformedReturnsFalse() {
    parseBadTraceId("463acL$c9f6413ad48485a3953bb6124");
    parseBadTraceId("holy 💩");
    parseBadTraceId("-");
    parseBadTraceId("");
    parseBadTraceId(null);
  }

  @Test public void parseSpanId() {
    String spanIdString = "48485a3953bb6124";

    TraceContext.Builder builder = parseGoodSpanId(spanIdString);

    assertThat(HexCodec.toLowerHex(builder.spanId))
        .isEqualTo(spanIdString);
  }

  @Test public void parseSpanId_short64bit() {
    String spanIdString = "6124";

    TraceContext.Builder builder = parseGoodSpanId(spanIdString);

    assertThat(HexCodec.toLowerHex(builder.spanId))
        .isEqualTo("000000000000" + spanIdString);
  }

  /**
   * Span ID is a required parameter, so it cannot be null empty malformed or other nonsense.
   *
   * <p>Notably, this shouldn't throw exception or allocate anything
   */
  @Test public void parseSpanId_malformedReturnsFalse() {
    parseBadSpanId("463acL$c9f6413ad");
    parseBadSpanId("holy 💩");
    parseBadSpanId("-");
    parseBadSpanId("");
    parseBadSpanId(null);
  }

  TraceContext.Builder parseGoodSpanId(String spanIdString) {
    TraceContext.Builder builder = TraceContext.newBuilder();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseSpanId(getter, spanIdString, "span-id"))
        .isTrue();
    return builder;
  }

  void parseBadSpanId(String spanIdString) {
    TraceContext.Builder builder = TraceContext.newBuilder();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseSpanId(getter, spanIdString, "span-id"))
        .isFalse();
    assertThat(builder.spanId).isZero();
  }

  @Test public void parseParentId() {
    String parentIdString = "48485a3953bb6124";

    TraceContext.Builder builder = parseGoodParentId(parentIdString);

    assertThat(HexCodec.toLowerHex(builder.parentId))
        .isEqualTo(parentIdString);
  }

  @Test public void parseParentId_null_is_ok() {
    TraceContext.Builder builder = parseGoodParentId(null);

    assertThat(builder.parentId).isZero();
  }

  @Test public void parseParentId_short64bit() {
    String parentIdString = "6124";

    TraceContext.Builder builder = parseGoodParentId(parentIdString);

    assertThat(HexCodec.toLowerHex(builder.parentId))
        .isEqualTo("000000000000" + parentIdString);
  }

  /**
   * Parent Span ID is an optional parameter, but it cannot be empty malformed or other nonsense.
   *
   * <p>Notably, this shouldn't throw exception or allocate anything
   */
  @Test public void parseParentId_malformedReturnsFalse() {
    parseBadParentId("463acL$c9f6413ad");
    parseBadParentId("holy 💩");
    parseBadParentId("-");
    parseBadParentId("");
  }

  TraceContext.Builder parseGoodParentId(String parentIdString) {
    TraceContext.Builder builder = TraceContext.newBuilder();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseParentId(getter, parentIdString, "parent-id"))
        .isTrue();
    return builder;
  }

  void parseBadParentId(String parentIdString) {
    TraceContext.Builder builder = TraceContext.newBuilder();
    Propagation.Getter<String, String> getter = (c, k) -> c;
    assertThat(builder.parseParentId(getter, parentIdString, "parent-id"))
        .isFalse();
    assertThat(builder.parentId).isZero();
  }

  TraceContext.Builder parseGoodTraceID(String traceIdString) {
    TraceContext.Builder builder = TraceContext.newBuilder();
    assertThat(builder.parseTraceId(traceIdString, "trace-id"))
        .isTrue();
    return builder;
  }

  void parseBadTraceId(String traceIdString) {
    TraceContext.Builder builder = TraceContext.newBuilder();
    assertThat(builder.parseTraceId(traceIdString, "trace-id"))
        .isFalse();
    assertThat(builder.traceIdHigh).isZero();
    assertThat(builder.traceId).isZero();
  }
}
