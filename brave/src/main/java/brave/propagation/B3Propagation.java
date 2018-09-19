package brave.propagation;

import java.util.Collections;
import java.util.List;

import static brave.internal.HexCodec.lenientLowerHexToUnsignedLong;
import static brave.internal.HexCodec.lowerHexToUnsignedLong;
import static brave.internal.HexCodec.toLowerHex;
import static java.util.Arrays.asList;

/**
 * Implements <a href="https://github.com/openzipkin/b3-propagation">B3 Propagation</a>
 */
public final class B3Propagation<K> implements Propagation<K> {

  public static final Propagation.Factory FACTORY = new Propagation.Factory() {
    @Override public <K> Propagation<K> create(KeyFactory<K> keyFactory) {
      return new B3Propagation<>(keyFactory);
    }

    @Override public boolean supportsJoin() {
      return true;
    }

    @Override public String toString() {
      return "B3PropagationFactory";
    }
  };

  /**
   * 128 or 64-bit trace ID lower-hex encoded into 32 or 16 characters (required)
   */
  static final String TRACE_ID_NAME = "X-B3-TraceId";
  /**
   * 64-bit span ID lower-hex encoded into 16 characters (required)
   */
  static final String SPAN_ID_NAME = "X-B3-SpanId";
  /**
   * 64-bit parent span ID lower-hex encoded into 16 characters (absent on root span)
   */
  static final String PARENT_SPAN_ID_NAME = "X-B3-ParentSpanId";
  /**
   * "1" means report this span to the tracing system, "0" means do not. (absent means defer the
   * decision to the receiver of this header).
   */
  static final String SAMPLED_NAME = "X-B3-Sampled";
  /**
   * "1" implies sampled and is a request to override collection-tier sampling policy.
   */
  static final String FLAGS_NAME = "X-B3-Flags";
  final K b3Key, traceIdKey, spanIdKey, parentSpanIdKey, sampledKey, debugKey;
  final List<K> fields;

  B3Propagation(KeyFactory<K> keyFactory) {
    this.b3Key = keyFactory.create("b3");
    this.traceIdKey = keyFactory.create(TRACE_ID_NAME);
    this.spanIdKey = keyFactory.create(SPAN_ID_NAME);
    this.parentSpanIdKey = keyFactory.create(PARENT_SPAN_ID_NAME);
    this.sampledKey = keyFactory.create(SAMPLED_NAME);
    this.debugKey = keyFactory.create(FLAGS_NAME);
    this.fields = Collections.unmodifiableList(
        asList(b3Key, traceIdKey, spanIdKey, parentSpanIdKey, sampledKey, debugKey)
    );
  }

  @Override public List<K> keys() {
    return fields;
  }

  @Override public <C> TraceContext.Injector<C> injector(Setter<C, K> setter) {
    if (setter == null) throw new NullPointerException("setter == null");
    return new B3Injector<>(this, setter);
  }

  static final class B3Injector<C, K> implements TraceContext.Injector<C> {
    final B3Propagation<K> propagation;
    final Setter<C, K> setter;

    B3Injector(B3Propagation<K> propagation, Setter<C, K> setter) {
      this.propagation = propagation;
      this.setter = setter;
    }

    @Override public void inject(TraceContext traceContext, C carrier) {
      setter.put(carrier, propagation.traceIdKey, traceContext.traceIdString());
      setter.put(carrier, propagation.spanIdKey, toLowerHex(traceContext.spanId()));
      long parentId = traceContext.parentIdAsLong();
      if (parentId != 0L) {
        setter.put(carrier, propagation.parentSpanIdKey, toLowerHex(parentId));
      }
      if (traceContext.debug()) {
        setter.put(carrier, propagation.debugKey, "1");
      } else if (traceContext.sampled() != null) {
        setter.put(carrier, propagation.sampledKey, traceContext.sampled() ? "1" : "0");
      }
    }
  }

  @Override public <C> TraceContext.Extractor<C> extractor(Getter<C, K> getter) {
    if (getter == null) throw new NullPointerException("getter == null");
    return new B3Extractor<>(this, getter);
  }

  static final class B3Extractor<C, K> implements TraceContext.Extractor<C> {
    final B3Propagation<K> propagation;
    final Getter<C, K> getter;
    final K b3Key;

    B3Extractor(B3Propagation<K> propagation, Getter<C, K> getter) {
      this.propagation = propagation;
      this.b3Key = propagation.b3Key;
      this.getter = getter;
    }

    @Override public TraceContextOrSamplingFlags extract(C carrier) {
      if (carrier == null) throw new NullPointerException("carrier == null");

      // try to extract single-header format
      String b3String = getter.get(carrier, b3Key);
      if (b3String != null) {
        TraceContextOrSamplingFlags extracted = B3SingleFormat.parseB3SingleFormat(b3String);
        if (extracted != null) return extracted;
      }

      // Start by looking at the sampled state as this is used regardless
      // Official sampled value is 1, though some old instrumentation send true
      String sampled = getter.get(carrier, propagation.sampledKey);
      Boolean sampledV = sampled != null
          ? sampled.equals("1") || sampled.equalsIgnoreCase("true")
          : null;
      boolean debug = "1".equals(getter.get(carrier, propagation.debugKey));

      String traceIdString = getter.get(carrier, propagation.traceIdKey);
      // It is ok to go without a trace ID, if sampling or debug is set
      if (traceIdString == null) {
        if (!debug && sampledV == null) return TraceContextOrSamplingFlags.EMPTY;
        SamplingFlags flags = debug ? SamplingFlags.DEBUG
            : sampledV ? SamplingFlags.SAMPLED : SamplingFlags.NOT_SAMPLED;
        return TraceContextOrSamplingFlags.create(flags);
      }

      // Try to parse the trace IDs into the context
      TraceContext.Builder result = TraceContext.newBuilder();
      if (result.parseTraceId(traceIdString, propagation.traceIdKey)
          && result.parseSpanId(getter, carrier, propagation.spanIdKey)
          && result.parseParentId(getter, carrier, propagation.parentSpanIdKey)) {
        if (sampledV != null) result.sampled(sampledV.booleanValue());
        if (debug) result.debug(true);
        return TraceContextOrSamplingFlags.create(result.build());
      }
      return TraceContextOrSamplingFlags.EMPTY; // trace context is malformed so return empty
    }
  }
}
