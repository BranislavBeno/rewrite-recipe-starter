package com.yourorg;

import org.jetbrains.annotations.NotNull;
import org.openrewrite.*;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import static java.util.Collections.emptyList;

public class NoConstantStaticImport extends Recipe {

    @Option(displayName = "Fully qualified name of the constant",
            example = "org.springframework.http.MediaType.APPLICATION_JSON_VALUE")
    private final String fullyQualifiedConstantName;

    public NoConstantStaticImport(String fullyQualifiedConstantName) {
        this.fullyQualifiedConstantName = fullyQualifiedConstantName;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "Remove statically imported constant";
    }

    @Override
    public @NotNull String getDescription() {
        return "Removes statically imported constants and replaces them with qualified references. For example, `separator` becomes `File.separator`.";
    }

    @Override
    public @NotNull TreeVisitor<?, ExecutionContext> getVisitor() {
        String type = fullyQualifiedConstantName.substring(0, fullyQualifiedConstantName.lastIndexOf('.'));
        return Preconditions.check(new UsesType<>(type, false),
                new ReplaceConstantWithAnotherConstantVisitor(fullyQualifiedConstantName));
    }

    private static class ReplaceConstantWithAnotherConstantVisitor extends JavaVisitor<ExecutionContext> {

        private final String owningType;
        private final String constantName;
        private final JavaType.FullyQualified fullyQualifiedOwningType;

        public ReplaceConstantWithAnotherConstantVisitor(String fullyQualifiedConstantName) {
            this.owningType = fullyQualifiedConstantName.substring(0, fullyQualifiedConstantName.lastIndexOf('.'));
            this.constantName = fullyQualifiedConstantName.substring(fullyQualifiedConstantName.lastIndexOf('.') + 1);
            this.fullyQualifiedOwningType = JavaType.ShallowClass.build(fullyQualifiedConstantName.substring(0, fullyQualifiedConstantName.lastIndexOf('.')));
        }

        @Override
        public @NotNull J visitFieldAccess(J.FieldAccess fieldAccess, @NotNull ExecutionContext ctx) {
            JavaType.Variable fieldType = fieldAccess.getName().getFieldType();
            if (isConstant(fieldType)) {
                return replaceFieldAccess(fieldAccess, fieldType);
            }
            return super.visitFieldAccess(fieldAccess, ctx);
        }

        @Override
        public @NotNull J visitIdentifier(J.Identifier identifier, @NotNull ExecutionContext ctx) {
            JavaType.Variable fieldType = identifier.getFieldType();
            if (isConstant(fieldType) && !isVariableDeclaration()) {
                return replaceFieldAccess(identifier, fieldType);
            }
            return super.visitIdentifier(identifier, ctx);
        }

        private J replaceFieldAccess(Expression expression, JavaType.Variable fieldType) {
            JavaType owner = fieldType.getOwner();
            while (owner instanceof JavaType.FullyQualified) {
                JavaType.FullyQualified fullyQualified = (JavaType.FullyQualified) owner;
                maybeRemoveImport(fullyQualified.getFullyQualifiedName());
                owner = fullyQualified.getOwningClass();
            }

            if (expression instanceof J.Identifier) {
                maybeAddImport(fullyQualifiedOwningType.getFullyQualifiedName(), false);
                return new J.Identifier(Tree.randomId(),
                        Space.EMPTY,
                        Markers.EMPTY,
                        emptyList(),
                        fullyQualifiedOwningType.getClassName() + "." + constantName,
                        fullyQualifiedOwningType,
                        null);
            } else if (expression instanceof J.FieldAccess) {
                maybeAddImport(fullyQualifiedOwningType.getFullyQualifiedName(), false);
                J.FieldAccess fieldAccess = (J.FieldAccess) expression;
                Expression target = fieldAccess.getTarget();
                J.Identifier name = fieldAccess.getName();
                if (target instanceof J.Identifier) {
                    target = ((J.Identifier) target).withType(fullyQualifiedOwningType).withSimpleName(fullyQualifiedOwningType.getClassName());
                } else {
                    target = (((J.FieldAccess) target).getName()).withType(fullyQualifiedOwningType).withSimpleName(fullyQualifiedOwningType.getClassName());
                }
                name = name
                        .withFieldType(fieldType.withOwner(fullyQualifiedOwningType).withName(constantName))
                        .withSimpleName(constantName);
                return fieldAccess
                        .withTarget(target)
                        .withName(name);
            }
            return expression;
        }

        private boolean isConstant(@Nullable JavaType.Variable varType) {
            return varType != null && TypeUtils.isOfClassType(varType.getOwner(), owningType) &&
                    varType.getName().equals(constantName);
        }

        private boolean isVariableDeclaration() {
            Cursor maybeVariable = getCursor().dropParentUntil(is -> is instanceof J.VariableDeclarations || is instanceof J.CompilationUnit);
            if (!(maybeVariable.getValue() instanceof J.VariableDeclarations)) {
                return false;
            }
            JavaType.Variable variableType = ((J.VariableDeclarations) maybeVariable.getValue()).getVariables().get(0).getVariableType();
            if (variableType == null) {
                return true;
            }

            JavaType.FullyQualified ownerFqn = TypeUtils.asFullyQualified(variableType.getOwner());
            if (ownerFqn == null) {
                return true;
            }

            return constantName.equals(((J.VariableDeclarations) maybeVariable.getValue()).getVariables().get(0).getSimpleName()) &&
                    owningType.equals(ownerFqn.getFullyQualifiedName());
        }
    }
}