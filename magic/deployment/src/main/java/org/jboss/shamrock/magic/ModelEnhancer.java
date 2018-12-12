package org.jboss.shamrock.magic;

import java.lang.reflect.Field;
import java.util.function.BiFunction;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class ModelEnhancer implements BiFunction<String, ClassVisitor, ClassVisitor> {

    @Override
    public ClassVisitor apply(String className, ClassVisitor outputClassVisitor) {
        return new ModelEnhancingClassVisitor(className, outputClassVisitor);
    }

    static class ModelEnhancingClassVisitor extends ClassVisitor {

        private Type thisClass;

        public ModelEnhancingClassVisitor(String className, ClassVisitor outputClassVisitor) {
            super(Opcodes.ASM6, outputClassVisitor);
            thisClass = Type.getType("L"+className.replace('.', '/')+";");
        }

        /*
          public static <T extends Model> T findById(Integer id){
            return (T) Model.findById(Fruit.class, id);
          }
          access: 9, name: findAll, descriptor: ()Ljava/util/List;, 
           signature: <T:Lorg/jboss/shamrock/magic/runtime/Model;>()Ljava/util/List<TT;>;, exceptions: null

         */
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            // FIXME: do not add method if already present 
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
        
        @Override
        public void visitEnd() {
            MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, 
                                                 "findById", 
                                                 "(Ljava/lang/Integer;)Lorg/jboss/shamrock/magic/runtime/Model;", 
                                                 "<T:Lorg/jboss/shamrock/magic/runtime/Model;>(Ljava/lang/Integer;)TT;", 
                                                 null);
            try {
                Class<?> klass = Class.forName("org.objectweb.asm.MethodWriter");
                Field field = klass.getDeclaredField("compute");
                field.setAccessible(true);
                System.err.println("Method visitor compute: "+field.get(mv));
            }catch(Exception x) {
                x.printStackTrace();
            }
            mv.visitParameter("id", 0);
            mv.visitCode();
            mv.visitLdcInsn(thisClass);
            mv.visitIntInsn(Opcodes.ALOAD, 0);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                               "org/jboss/shamrock/magic/runtime/Model", 
                               "findById", 
                               "(Ljava/lang/Class;Ljava/lang/Integer;)Lorg/jboss/shamrock/magic/runtime/Model;", false);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();

            mv = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, 
                                                 "findAll", 
                                                 "()Ljava/util/List;", 
                                                 "<T:Lorg/jboss/shamrock/magic/runtime/Model;>()Ljava/util/List<TT;>;", 
                                                 null);
            mv.visitCode();
            mv.visitLdcInsn(thisClass);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, 
                               "org/jboss/shamrock/magic/runtime/Model", 
                               "findAll", 
                               "(Ljava/lang/Class;)Ljava/util/List;", false);
            mv.visitInsn(Opcodes.ARETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            
            super.visitEnd();
        }
    }
}
