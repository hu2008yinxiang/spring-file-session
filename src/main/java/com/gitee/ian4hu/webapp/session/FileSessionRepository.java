package com.gitee.ian4hu.webapp.session;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.session.MapSession;
import org.springframework.session.SessionRepository;
import org.springframework.util.Assert;

public class FileSessionRepository implements SessionRepository<MapSession> {

    private static final Log logger = LogFactory.getLog(FileSessionRepository.class);

    private int defaultMaxInactiveInterval = MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;

    private File storageDirectory = new File(System.getProperty("tmp.dir", "/tmp"), "sess");

    private boolean cleanExpireOnStartup = false;

    private boolean cleanNotReadable = true;

    private long nextCleanup;

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
        result.setMaxInactiveIntervalInSeconds(defaultMaxInactiveInterval);
        return result;
    }

    /**
     * set the storage path
     *
     * @param uri path
     */
    public void setStorageDirectory(String uri) {
        Assert.notNull(uri, "Storage directory should not be null");
        File path = new File(uri);
        // directory exists
        if ((path.isDirectory() || path.mkdirs()) && path.canWrite()) {
            this.storageDirectory = path;
            nextCleanup = System.currentTimeMillis(); // reset to do clean
        } else {
            throw new IllegalArgumentException(String.format("Path '%s' is not a directory or can't write.", path));
        }
    }

    /**
     * get the storage path
     *
     * @return File
     */
    public File getStorageDirectory() {
        if (!storageDirectory.isDirectory() && !storageDirectory.mkdirs()) {
            logger.warn("Storage directory is not a directory @ " + storageDirectory.getAbsolutePath());
        }

        if (nextCleanup < System.currentTimeMillis() && isCleanExpireOnStartup()) {
            nextCleanup = System.currentTimeMillis() + defaultMaxInactiveInterval;
            doCleanExpired();
        }

        return storageDirectory;
    }

    /**
     * Manually clean the expired
     */
    public void doCleanExpired() {
        File[] files = storageDirectory.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (!f.isFile() || !f.exists()) {
                continue;
            }

            String id = f.getName();

            // Clean expire will occur after read
            readSessionFile(id);
        }
    }

    public void save(MapSession session) {
        String id = session.getId();
        File file = new File(getStorageDirectory(), id);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(session);
            oos.flush();
            oos.close();
        } catch (IOException e) {
            logger.error("Session file write failed @ " + file.getAbsolutePath(), e);
            throw new IllegalStateException("Session read failed", e);
        }
    }


    public MapSession getSession(String id) {
        MapSession session = readSessionFile(id);
        if (session == null) {
            return null;
        }
        // Update last access time
        session.setLastAccessedTime(System.currentTimeMillis());
        return session;
    }

    public void delete(String id) {
        File file = new File(getStorageDirectory(), id);
        if (file.exists() && !file.delete()) {
            logger.warn("Failed to delete session file @ " + file.getAbsolutePath() + ", session may not be deleted.");
        }
    }

    private MapSession readSessionFile(String id) {
        File file = new File(getStorageDirectory(), id);
        if (!file.isFile()) {
            logger.warn("Session file @ " + file.getAbsolutePath() + " is not a file.");
            return null;
        }

        Object sessionObj = null;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            sessionObj = ois.readObject();
            ois.close();
        } catch (IOException e) {
            logger.warn("Session file @ " + file.getAbsolutePath() + " read failed.", e);
            // it's ok to fail
            if (isCleanNotReadable()) {
                delete(id);
            }
        } catch (ClassNotFoundException e) {
            logger.warn("Session read failed @ " + file.getAbsolutePath(), e);
            // it's ok to fail
            if (isCleanNotReadable()) {
                delete(id);
            }
        }

        if (!(sessionObj instanceof MapSession)) {
            // Meet data corruption
            delete(id);
            return null;
        }

        MapSession session = (MapSession) sessionObj;
        // expired
        if (session.isExpired()) {
            delete(id);
            return null;
        }
        if (!session.getId().equals(id)) {
            // Meet data corruption
            logger.warn("Session with wrong session id " + session.getId() + " @ " + file.getAbsolutePath());
            delete(id);
            return null;
        }
        return session;

    }
}
