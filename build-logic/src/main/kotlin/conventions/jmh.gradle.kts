package conventions

plugins {
    id("me.champeau.jmh")
}

jmh {
    fork.set(1)
    warmupIterations.set(1)
    iterations.set(2)
}
