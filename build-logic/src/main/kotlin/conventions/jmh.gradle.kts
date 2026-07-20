package conventions

plugins {
    id("me.champeau.jmh")
}

// Single source of truth for JMH run settings. me.champeau.jmh applies these over any
// @Fork/@Warmup/@Measurement annotations, so benchmarks must NOT also declare iteration counts or
// times in annotations (they would be silently overridden). fork = 1 keeps runs cheap but means
// JMH reports no error interval, so treat the recorded scores as directional, not precise.
jmh {
    fork.set(1)
    warmupIterations.set(1)
    warmup.set("2s")
    iterations.set(2)
    timeOnIteration.set("2s")
}
