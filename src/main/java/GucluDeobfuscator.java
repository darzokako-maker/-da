import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class GucluDeobfuscator {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Kullanim: java -jar deobfuscator.jar <giris_oyun.jar> <cikis_temiz.jar>");
            return;
        }

        File inputFile = new File(args[0]);
        File outputFile = new File(args[1]);

        // JarFile yerine ZipInputStream kullanarak bozuk başlık korumasını (END header bypass) geçiyoruz
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(inputFile));
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputFile))) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                
                // Ham baytları oku
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    bos.write(buffer, 0, len);
                }
                byte[] entryBytes = bos.toByteArray();

                // Eğer dosya bir Java Sınıfı (.class) ise ve içi boş değilse analiz et
                if (entry.getName().endsWith(".class") && entryBytes.length > 0) {
                    try {
                        ClassReader reader = new ClassReader(entryBytes);
                        ClassNode classNode = new ClassNode();
                        reader.accept(classNode, 0);

                        // Metotları ve şifreli Stringleri tara
                        for (MethodNode method : classNode.methods) {
                            InsnList instructions = method.instructions;
                            for (AbstractInsnNode insn : instructions.toArray()) {
                                if (insn.getOpcode() == Opcodes.LDC) {
                                    LdcInsnNode ldc = (LdcInsnNode) insn;
                                    if (ldc.cst instanceof String) {
                                        String encrypted = (String) ldc.cst;
                                        String decrypted = decryptString(encrypted);
                                        ldc.cst = decrypted;
                                    }
                                }
                            }
                        }

                        // Yeniden derle ve yaz
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                        classNode.accept(writer);
                        entryBytes = writer.toByteArray();
                    } catch (Exception e) {
                        // Sınıf okunurken hata alınırsa dosyayı bozmamak için orijinal halini bırak
                        System.out.println("[-] Sinif okunurken atlandi: " + entry.getName());
                    }
                }

                // Sonucu yeni arşive ekle
                ZipEntry newEntry = new ZipEntry(entry.getName());
                zos.putNextEntry(newEntry);
                zos.write(entryBytes);
                zos.closeEntry();
                zis.closeEntry();
            }
            System.out.println("[BAŞARILI] Bozuk arşiv koruması geçildi, şifreler çözüldü!");

        } catch (Exception e) {
            System.out.println("[HATA] Pipeline akis hatasi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String decryptString(String input) {
        if (input == null || input.isEmpty()) return input;
        char[] key = {'S', 'O', 'C'}; 
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            output.append((char) (input.charAt(i) ^ key[i % key.length]));
        }
        return output.toString();
    }
}
