package com.yourorg;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class NoConstantStaticImportTest implements RewriteTest {

    @Test
    void removeTopLevelClassImport() {
        rewriteRun(
                spec -> spec.recipe(new NoConstantStaticImport("foo.Bar.Baz.QUX")),
                java(
                        """
                                package foo;
                                                                
                                public class Bar {
                                    public static class Baz {
                                        public static final String QUX = "QUX";
                                    }
                                }
                                """
                ),
                java(
                        """
                                import static foo.Bar.Baz.QUX;
                                                                
                                class Test {
                                    Object o = QUX;
                                }
                                """,
                        """
                                import foo.Bar.Baz;
                                                                
                                class Test {
                                    Object o =Baz.QUX;
                                }
                                """
                )
        );
    }

    @Test
    void replaceConstant() {
        rewriteRun(
                spec -> spec.recipe(new NoConstantStaticImport("java.io.File.pathSeparator")),
                java(
                        """
                                import static java.io.File.pathSeparator;
                                                                
                                class Test {
                                    void foo() {
                                        System.out.println(pathSeparator);
                                    }
                                }
                                """,
                        """
                                import java.io.File;
                                                                
                                class Test {
                                    void foo() {
                                        System.out.println(File.pathSeparator);
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void replaceConstantInAnnotation() {
        rewriteRun(
                spec -> spec.recipe(new NoConstantStaticImport("foo.B.ATTRIBUTE_FOO")),
                java(
                        """
                                package foo;

                                public class B {
                                    public static final String ATTRIBUTE_FOO = "foo";
                                }
                                """
                ),
                java(
                        """
                                import static foo.B.ATTRIBUTE_FOO;

                                @SuppressWarnings(ATTRIBUTE_FOO)
                                class Example {
                                    @SuppressWarnings(value = ATTRIBUTE_FOO)
                                    void foo() {
                                        System.out.println("Annotation");
                                    }
                                }
                                """,
                        """
                                import foo.B;
                                                                
                                @SuppressWarnings(B.ATTRIBUTE_FOO)
                                class Example {
                                    @SuppressWarnings(value =B.ATTRIBUTE_FOO)
                                    void foo() {
                                        System.out.println("Annotation");
                                    }
                                }
                                """
                )
        );
    }

    @Test
    void replaceConstantInCurlyBracesInAnnotation() {
        rewriteRun(
                spec -> spec.recipe(new NoConstantStaticImport("foo.B.ATTRIBUTE_FOO")),
                java(
                        """
                                package foo;

                                public class B {
                                    public static final String ATTRIBUTE_FOO = "foo";
                                }
                                """
                ),
                java(
                        """
                                import static foo.B.ATTRIBUTE_FOO;

                                @SuppressWarnings({ATTRIBUTE_FOO})
                                class Example {
                                }
                                """,
                        """
                                import foo.B;
                                                                
                                @SuppressWarnings({B.ATTRIBUTE_FOO})
                                class Example {
                                }
                                """
                )
        );
    }

    @Test
    @Disabled
    void replaceConstantForAnnotatedParameter() {
        rewriteRun(
                spec -> spec.recipe(new NoConstantStaticImport("foo.B.ATTRIBUTE_FOO")),
                java(
                        """
                                package foo;

                                public class B {
                                    public static final String ATTRIBUTE_FOO = "foo";
                                }
                                """
                ),
                java(
                        """
                                import static foo.B.ATTRIBUTE_FOO;

                                class Example {
                                    void foo(@SuppressWarnings(value = ATTRIBUTE_FOO) String param) {
                                        System.out.println(param);
                                    }
                                }
                                """,
                        """
                                import foo.B;
                                                                
                                class Example {
                                    void foo(@SuppressWarnings(value =B.ATTRIBUTE_FOO) String param) {
                                        System.out.println(param);
                                    }
                                }
                                """
                )
        );
    }
}