package ian.hu.webapp.session;

import org.springframework.session.MapSession;
import org.springframework.session.SessionRepository;

import java.io.*;

public class FileSessionRepository implements SessionRepository<MapSession> {

    private Integer defaultMaxInactiveInterval;
    private File storageDirectory;
    private boolean cleanExpireOnStartup = false;
    private boolean cleanIsDone = false;
    private boolean cleanNotReadable = false;

    /**
     * Whether should clean up the storage directory when startup, this will delete the expired session files or unreadable files.
     *
     * @return boolean
     */
    public boolean isCleanExpireOnStartup() {
        return cleanExpireOnStartup;
    }

    /**
     * Whether should clean up the storage directory when startup, this will delete the expired session files or unreadable files.
     *
     * @param cleanExpireOnStartup true/false
     */
    public void setCleanExpireOnStartup(boolean cleanExpireOnStartup) {
        this.cleanExpireOnStartup = cleanExpireOnStartup;
    }

    /**
     * Whether should clean the unreadable files
     *
     * @return boolean
     */
    public boolean isCleanNotReadable() {
        return cleanNotReadable;
    }

    /**
     * hether should clean the unreadable files
     *
     * @param cleanNotReadable boolean
     */
    public void setCleanNotReadable(boolean cleanNotReadable) {
        this.cleanNotReadable = cleanNotReadable;
    }

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

    /**
     * set the storage path
     *
     * @param uri path
     */
    public void setStorageDirectory(String uri) {
        File path = new File(uri);
        // directory exists
        if ((path.isDirectory() || path.mkdirs()) && path.canWrite()) {
            this.storageDirectory = path;
            cleanIsDone = false; // reset to do clean
        } else {
            throw new RuntimeException(new IOException(String.format("Path '%s' is not a directory or can't write.", path)));
        }
    }

    /**
     * get the storage path
     *
     * @return File
     */
    public File getStorageDirectory() {
        if (storageDirectory == null) {
            setStorageDirectory(System.getProperty("tmp.dir", "/tmp"));
        }
        if (!cleanIsDone && cleanExpireOnStartup) {
            cleanIsDone = true;
            doCleanExpired();
        }
        return storageDirectory;
    }

    /**
     * Manually clean the expired
     */
    public void doCleanExpired() {
        if (storageDirectory == null || !storageDirectory.canWrite()) {
            throw new RuntimeException("storageDirectory should not be null and must be writable to clean.");
        }
        File[] files = storageDirectory.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (!f.isFile() || !f.exists()) {
                continue;
            }
            String id = f.getName();
            try {
                ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
                Object obj = ois.readObject();
                ois.close();
                // not readable
                if (obj == null || !(obj instanceof MapSession)) {
                    if (cleanNotReadable) {
                        f.delete();
                    }
                    continue;
                }
                MapSession session = (MapSession) obj;
                // expired
                if (session.isExpired()) {
                    f.delete();
                    continue;
                }
            } catch (IOException e) {
                // it's ok to fail
                if (cleanNotReadable) {
                    f.delete();
                }
            } catch (ClassNotFoundException e) {
                // it's ok to fail
                if (cleanNotReadable) {
                    f.delete();
                }
            }
        }
    }

    public void save(MapSession session) {
        String id = session.getId();
        File file = new File(getStorageDirectory(), id);
        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
            oos.writeObject(session);
            oos.flush();
            oos.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
