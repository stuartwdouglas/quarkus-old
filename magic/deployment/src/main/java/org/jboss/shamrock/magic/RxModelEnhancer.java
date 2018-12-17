package org.jboss.shamrock.magic;

import java.util.function.BiFunction;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class RxModelEnhancer implements BiFunction<String, ClassVisitor, ClassVisitor> {

    @Override
    public ClassVisitor apply(String className, ClassVisitor outputClassVisitor) {
        return new ModelEnhancingClassVisitor(className, outputClassVisitor);
    }

    static class ModelEnhancingClassVisitor extends ClassVisitor {

        private String thisName;
        private boolean defaultConstructorPresent;

        public ModelEnhancingClassVisitor(String className, ClassVisitor outputClassVisitor) {
            super(Opcodes.ASM6, outputClassVisitor);
            thisName = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if("<init>".equals(name) && "()V".equals(descriptor))
                defaultConstructorPresent = true;
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
        
        @Override
        public void visitEnd() {
            // no-arg constructor
            MethodVisitor mv;
            if(!defaultConstructorPresent) {
                mv = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, 
                                       "<init>", 
                                       "()V", 
                                       null, 
                                       null);
                mv.visitCode();
                mv.visitIntInsn(Opcodes.ALOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, 
                                   "org/jboss/shamrock/magic/runtime/RxModel", 
                                   "<init>", 
                                   "()V", false);
                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(0, 0);
                mv.visitEnd();
            }

            // model field
            String fieldName = "$$MODEL";
            String modelName = thisName + "$__MODEL";
            String modelType = modelName.replace('.', '/');
            String modelDesc = "L"+modelType+";";
            super.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC, fieldName, modelDesc, null, null).visitEnd();
            
            // model field init
            MethodVisitor staticInit = super.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, "<clinit>", "()V", null, null);
            staticInit.visitTypeInsn(Opcodes.NEW, modelType);
            staticInit.visitInsn(Opcodes.DUP);
            staticInit.visitMethodInsn(Opcodes.INVOKESPECIAL, 
                                       modelType, 
                                       "<init>", 
                                       "()V", false);
            staticInit.visitFieldInsn(Opcodes.PUTSTATIC, thisName.replace('.', '/'), fieldName, modelDesc);
            staticInit.visitInsn(Opcodes.RETURN);
            staticInit.visitMaxs(0, 0);
            staticInit.visitEnd();
            
            // getModelInfo
            mv = super.visitMethod(Opcodes.ACC_PROTECTED | Opcodes.ACC_SYNTHETIC, 
                                                 "getModelInfo", 
                                                 "()Lorg/jboss/shamrock/magic/runtime/RxModel$RxModelInfo;", 
                                                 "()Lorg/jboss/shamrock/magic/runtime/RxModel$RxModelInfo<+Lorg/jboss/shamrock/magic/runtime/RxModel;>;", 
                                                 null);
            mv.visitCode();
            mv.visitFieldInsn(Opcodes.GETSTATIC, thisName.replace('.', '/'), fieldName, modelDesc);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            
            // findById
            mv = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, 
                                                 "findById", 
                                                 "(Ljava/lang/Integer;)Lio/reactivex/Maybe;", 
                                                 "(Ljava/lang/Integer;)Lio/reactivex/Maybe<Lorg/jboss/shamrock/magic/runtime/Model;>;", 
                                                 null);
            mv.visitParameter("id", 0);
            mv.visitCode();
            mv.visitFieldInsn(Opcodes.GETSTATIC, thisName.replace('.', '/'), fieldName, modelDesc);
            mv.visitIntInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                               "org/jboss/shamrock/magic/runtime/RxModel", 
                               "findById", 
                               "(Lorg/jboss/shamrock/magic/runtime/RxModel$RxModelInfo;Ljava/lang/Integer;)Lio/reactivex/Maybe;", false);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            // findAll
            mv = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, 
                                                 "findAll", 
                                                 "()Lio/reactivex/Observable;", 
                                                 "()Lio/reactivex/Observable<Lorg/jboss/shamrock/magic/runtime/RxModel;>;", 
                                                 null);
            mv.visitCode();
            mv.visitFieldInsn(Opcodes.GETSTATIC, thisName.replace('.', '/'), fieldName, modelDesc);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                               "org/jboss/shamrock/magic/runtime/RxModel", 
                               "findAll", 
                               "(Lorg/jboss/shamrock/magic/runtime/RxModel$RxModelInfo;)Lio/reactivex/Observable;", false);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            
            // FIXME: inner class?
            super.visitEnd();
            
        }
    }
}
