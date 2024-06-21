# Roadmap

## Towards version 1

Currently, you need to use bind when working with dynamic values, but we aim to achieve this with string interpolation
alone, similar to Scala's [Doobie](https://tpolecat.github.io/doobie/).

However, Kotlin does not have a mechanism to customize string interpolation.

We plan to achieve this by using a Kotlin compiler plugin. Once this is achieved, we plan to release version 1.

On the other hand, recently, such a mechanism has been introduced in Java. Therefore, it might be officially supported
in Kotlin soon. (There is a ticket)
https://youtrack.jetbrains.com/issue/KT-64632/Support-Java-21-StringTemplate.Processor

However, we might release version 1 as it is now and defer the plan mentioned above to version 2.
