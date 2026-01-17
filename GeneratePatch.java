import com.orange.patchgen.PatchGenerator;
import com.orange.patchgen.callback.GeneratorCallback;
import com.orange.patchgen.config.EngineType;
import com.orange.patchgen.config.PatchMode;
import com.orange.patchgen.model.PatchResult;

import java.io.File;

public class GeneratePatch {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: java GeneratePatch <base.apk> <new.apk> <output.zip>");
            System.exit(1);
        }
        
        File baseApk = new File(args[0]);
        File newApk = new File(args[1]);
        File outputFile = new File(args[2]);
        
        if (!baseApk.exists()) {
            System.err.println("Base APK not found: " + baseApk);
            System.exit(1);
        }
        
        if (!newApk.exists()) {
            System.err.println("New APK not found: " + newApk);
            System.exit(1);
        }
        
        System.out.println("Generating patch...");
        System.out.println("  Base: " + baseApk.getAbsolutePath());
        System.out.println("  New:  " + newApk.getAbsolutePath());
        System.out.println("  Output: " + outputFile.getAbsolutePath());
        
        PatchGenerator generator = new PatchGenerator.Builder()
            .baseApk(baseApk)
            .newApk(newApk)
            .output(outputFile)
            .engineType(EngineType.JAVA)
            .patchMode(PatchMode.FULL_DEX)
            .callback(new GeneratorCallback() {
                @Override
                public void onProgress(int percent, String stage) {
                    System.out.println("[" + percent + "%] " + stage);
                }
                
                @Override
                public void onComplete(PatchResult result) {
                    if (result.isSuccess()) {
                        System.out.println("\nPatch generated successfully!");
                        System.out.println("  Size: " + formatSize(result.getPatchSize()));
                        System.out.println("  Time: " + result.getGenerateTime() + " ms");
                    } else {
                        System.err.println("\nFailed: " + result.getErrorMessage());
                    }
                }
                
                @Override
                public void onError(int errorCode, String message) {
                    System.err.println("\nError: " + message);
                }
            })
            .build();
        
        PatchResult result = generator.generate();
        
        if (!result.isSuccess()) {
            System.exit(1);
        }
    }
    
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
