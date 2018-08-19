package com.gitee.ian4hu.webapp.session;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.session.MapSession;
import org.springframework.util.FileCopyUtils;

import static org.junit.Assert.*;

public class FileSessionRepositoryTest {

    private final FileSessionRepository repo = new FileSessionRepository();

    private final MapSession savedSession = repo.createSession();

    private final MapSession expiredSession = repo.createSession();

    private final MapSession wrongSessionId = repo.createSession();

    private final File wrongSessionIdFile = new File(repo.getStorageDirectory(), "wrong-session-id");

    private final File errorSessionFile = new File(repo.getStorageDirectory(), "error-session");

    @Before
    public void setUp() throws Exception {
        repo.setCleanExpireOnStartup(true);
        repo.setCleanNotReadable(true);
        repo.setDefaultMaxInactiveInterval(1800);
        repo.setStorageDirectory(repo.getStorageDirectory().getAbsolutePath());
        repo.save(savedSession);
        expiredSession.setLastAccessedTime(Instant.ofEpochSecond(1));
        repo.save(expiredSession);
        repo.save(wrongSessionId);
        File file = new File(repo.getStorageDirectory(), wrongSessionId.getId());
        FileCopyUtils.copy(file, wrongSessionIdFile);
    }

    @Test
    public void createSession() {
        MapSession session = repo.createSession();
        assertNotNull(session);
        assertNotNull(session.getId());
    }

    @Test
    public void save() {
        MapSession session = repo.createSession();
        repo.save(session);
        assertEquals(session, repo.findById(session.getId()));
    }

    @Test
    public void getSession() {
        MapSession session = repo.findById(savedSession.getId());
        assertEquals(savedSession, session);
    }

    @Test
    public void delete() {
        MapSession session = repo.createSession();
        repo.save(session);
        repo.deleteById(session.getId());
        assertNull(repo.findById(session.getId()));
    }

    @After
    public void tearDown() {
        repo.deleteById(savedSession.getId());
        wrongSessionIdFile.delete();
    }

    @Test
    public void deleteNotExists() {
        repo.deleteById("not-exists");
        assertNull(repo.findById("not-exists"));
    }

    @Test
    public void getSessionNotExists() {
        MapSession session = repo.findById("not-exists");
        assertNull(session);
    }

    @Test
    public void getSessionNotRead() throws IOException {
        assertTrue(errorSessionFile.createNewFile());
        MapSession session = repo.findById("error-session");
        assertNull(session);
        assertFalse(errorSessionFile.exists());
    }

    @Test
    public void getSessionExpired() {
        MapSession session = repo.findById(expiredSession.getId());
        assertNull(session);
    }

    @Test
    public void getSessionWrongSessionId() {
        MapSession session = repo.findById("wrong-session-id");
        assertNull(session);
    }

    @Test
    public void doClean() {
        repo.doCleanExpired();
        assertNull(repo.findById("error-session"));
        assertNull(repo.findById(expiredSession.getId()));
    }
}
