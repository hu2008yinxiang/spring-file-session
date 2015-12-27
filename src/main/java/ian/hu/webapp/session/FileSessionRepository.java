package ian.hu.webapp.session;

import org.springframework.session.MapSession;
import org.springframework.session.SessionRepository;

import java.io.*;

public class FileSessionRepository implements SessionRepository<MapSession> {

    private Integer defaultMaxInactiveInterval;
    private File storageDirectory;


    public void setDefaultMaxInactiveInterval(int defaultMaxInactiveInterval) {
        this.defaultMaxInactiveInterval = defaultMaxInactiveInterval;
    }

    public MapSession createSession() {
        MapSession result = new MapSession();
        if (defaultMaxInactiveInterval != null) {
            result.setMaxInactiveIntervalInSeconds(defaultMaxInactiveInterval);
        }
        return result;
    }

    public void setStorageDirectory(String uri) {
        File path = new File(uri);
        // directory exists
        if ((path.isDirectory() || path.mkdirs()) && path.canWrite()) {
            this.storageDirectory = path;
        } else {
            throw new RuntimeException(new IOException(String.format("Path '%s' is not a directory or can't write.", path)));
        }
    }

    public void save(MapSession session) {
        String id = session.getId();
        File file = new File(getStorageDirectory(), id);
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
            oos.writeObject(oos);
            oos.flush();
            oos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public File getStorageDirectory() {
        if (storageDirectory == null) {
            setStorageDirectory(System.getProperty("tmp.dir", "/tmp"));
        }
        return storageDirectory;
    }

    public MapSession getSession(String id) {
        File file = new File(getStorageDirectory(), id);
        if (file.isFile()) {
            try {
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file));
                Object sessionObj = ois.readObject();
                ois.close();

                if (sessionObj == null) {
                    return null;
                }
                if (!(sessionObj instanceof MapSession)) {
                    throw new RuntimeException(String.format("Object in session file '%s' is not a MapSession", file.getPath()));
                }
                MapSession session = (MapSession) sessionObj;
                if (session.isExpired()) {
                    delete(id);
                    return null;
                }
                if (!session.getId().equals(id)) {
                    throw new RuntimeException("Session id is not equal the session file name.");
                }
                session.setLastAccessedTime(System.currentTimeMillis());
                return session;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    public void delete(String id) {
        File file = new File(getStorageDirectory(), id);
        if (file.exists() && !file.delete()) {
            throw new RuntimeException("Session file '%s' delete failed, session can not be destroyed.");
        }
    }
}
