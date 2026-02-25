package ltldev.SeverUpFile.util;

import org.springframework.core.io.Resource;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;

public class FileRangeResource implements Resource {

    private final File file;
    private final long position;
    private final long size;

    public FileRangeResource(File file, long position, long size) {
        this.file = file;
        this.position = position;
        this.size = size;
    }

    /**
     * ✅ Stream file từ position đến size (chunk by chunk)
     */
    @Override
    public InputStream getInputStream() throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        raf.seek(position);

        // Wrap để đóng file khi stream đóng
        return new FilterInputStream(new FileInputStream(raf.getFD())) {
            @Override
            public void close() throws IOException {
                super.close();
                raf.close();
            }
        };
    }

    @Override
    public long contentLength() throws IOException {
        return size;
    }

    @Override
    public String getFilename() {
        return file.getName();
    }

    @Override
    public long lastModified() throws IOException {
        return file.lastModified();
    }

    @Override
    public boolean exists() {
        return file.exists();
    }

    @Override
    public boolean isReadable() {
        return file.canRead();
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public boolean isFile() {
        return true;
    }

    @Override
    public URL getURL() throws IOException {
        return file.toURI().toURL();
    }

    @Override
    public URI getURI() throws IOException {
        return file.toURI();
    }

    @Override
    public File getFile() throws IOException {
        return file;
    }

    @Override
    public ReadableByteChannel readableChannel() throws IOException {
        return Files.newByteChannel(file.toPath());
    }

    @Override
    public Resource createRelative(String relativePath) throws IOException {
        return null;
    }

    @Override
    public String getDescription() {
        return "File range resource [" + file.getAbsolutePath() + "]";
    }
}