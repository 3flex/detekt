@file:Suppress("unused")

package cases

internal class NestedClassVisibilityNegative {

    // enums with public visibility are excluded
    enum class NestedEnum { One, Two }

    private interface PrivateTest
    internal interface InternalTest

    // should not detect companion object
    companion object C

    class Something {}
}

private class PrivateClassWithNestedElements {

    class Inner
}

internal interface IgnoreNestedClassInInterface {

    class Nested
}

//internal sealed class MyTest { //NestedClassesVisibility violation
//    object MyTest2: MyTest()
//    class MyTest3: MyTest()
//}
