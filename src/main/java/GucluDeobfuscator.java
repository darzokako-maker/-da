import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class GucluDeobfuscator {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Kullanim: java -jar deobfuscator.jar <giris_oyun.jar> <cikis_temiz.jar>");
            return;
        }

        File inputFile = new File(args[0]);
        File outputFile = new File(args[1]);

        try (JarFile inputJar = new JarFile(inputFile);
             JarOutputStream outputJar = new JarOutputStream(new FileOutputStream(outputFile))) {

            Enumeration<JarEntry> entries = inputJar.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                try (InputStream entryStream = inputJar.getInputStream(entry)) {
                    
                    // Eğer dosya bir Java Sınıfı (.class) ise analiz et
                    if (entry.getName().endsWith(".class")) {
                        byte[] classBytes = entryStream.readAllBytes();
                        
                        ClassReader reader = new ClassReader(classBytes);
                        ClassNode classNode = new ClassNode();
                        reader.accept(classNode, 0);

                        // Sınıfın içindeki metotları tara
                        for (MethodNode method : classNode.methods) {
                            InsnList instructions = method.instructions;
                            for (AbstractInsnNode insn : instructions.toArray()) {
                                
                                // Şifreli String yüklemelerini (LDC) yakala
                                if (insn.getOpcode() == Opcodes.LDC) {
                                    LdcInsnNode ldc = (LdcInsnNode) insn;
                                    if (ldc.cst instanceof String) {
                                        String encrypted = (String) ldc.cst;
                                        
                                        // Şifreyi Çöz ve Değiştir
                                        String decrypted = decryptString(encrypted);
                                        ldc.cst = decrypted;
                                    }
                                }
                            }
                        }

                        // Değiştirilmiş sınıfı yeni jar'a yaz
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                        classNode.accept(writer);
                        outputJar.putNextEntry(new JarEntry(entry.getName()));
                        outputJar.write(writer.toByteArray());
                        outputJar.closeEntry();

                    } else {
                        // Resim, ses vb. diğer dosyaları değiştirmeden direkt kopyala
                        outputJar.putNextEntry(new JarEntry(entry.getName()));
                        outputJar.write(entryStream.readAllBytes());
                        outputJar.closeEntry();
                    }
                }
            }
            System.out.println("[BAŞARILI] Şifreler çözüldü ve yeni temiz dosya oluşturuldu!");

        } catch (Exception e) {
            System.out.println("[HATA] İşlem sırasında bir sorun oluştu: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Basit XOR/Karakter Kaydırma Çözücü Altyapısı
    // (Oyunun güncel şifreleme tekniğine göre burayı ileride modifiye edebilirsin)
    private static String decryptString(String input) {
        if (input == null || input.isEmpty()) return input;
        char[] key = {'S', 'O', 'C'}; // Örnek anahtar
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            output.append((char) (input.charAt(i) ^ key[i % key.length]));
        }
        return output.toString();
    }
}

