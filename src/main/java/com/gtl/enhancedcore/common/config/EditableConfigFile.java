package com.gtl.enhancedcore.common.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** A bounded edit session; validation and conflict checks precede the atomic write. */
public final class EditableConfigFile {
    private final Path path;
    private String original;
    public EditableConfigFile(Path path) throws IOException { this.path = path; reload(); }
    public String text() { return original == null ? "" : original; }
    public boolean exists() { return original != null; }
    public void reload() throws IOException { original = read(); }
    private String read() throws IOException {
        if (!Files.exists(path)) return null;
        if (Files.size(path) > 8_000_000) throw new IOException("Configuration exceeds 8 MB: " + path.getFileName());
        return Files.readString(path);
    }
    public void save(String text) throws IOException {
        if (text.length() > TooltipOverrides.MAX_CHARACTERS) throw new IOException("Configuration exceeds 2,000,000 characters");
        if (!java.util.Objects.equals(original, read())) throw new IOException("File changed outside this editor; reopen before saving");
        ConfigFiles.writeAtomically(path, text);
        original = text;
    }
}
