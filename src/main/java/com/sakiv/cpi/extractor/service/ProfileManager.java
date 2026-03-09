package com.sakiv.cpi.extractor.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sakiv.cpi.extractor.model.ConnectionProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages connection profiles stored in ~/.cpi-extractor/profiles.json.
 */
public class ProfileManager {

    private static final Logger log = LoggerFactory.getLogger(ProfileManager.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Path profileDir;
    private final Path profileFile;

    public ProfileManager() {
        this.profileDir = Path.of(System.getProperty("user.home"), ".cpi-extractor");
        this.profileFile = profileDir.resolve("profiles.json");
    }

    public List<ConnectionProfile> loadProfiles() {
        if (!Files.exists(profileFile)) {
            return new ArrayList<>();
        }
        try {
            List<ConnectionProfile> profiles = mapper.readValue(
                    profileFile.toFile(), new TypeReference<List<ConnectionProfile>>() {});
            log.info("Loaded {} connection profiles from {}", profiles.size(), profileFile);
            return profiles;
        } catch (IOException e) {
            log.warn("Failed to load profiles from {}: {}", profileFile, e.getMessage());
            return new ArrayList<>();
        }
    }

    public void saveProfiles(List<ConnectionProfile> profiles) {
        try {
            Files.createDirectories(profileDir);
            mapper.writeValue(profileFile.toFile(), profiles);
            log.info("Saved {} connection profiles to {}", profiles.size(), profileFile);
        } catch (IOException e) {
            log.error("Failed to save profiles to {}: {}", profileFile, e.getMessage());
            throw new RuntimeException("Failed to save profiles: " + e.getMessage(), e);
        }
    }

    public void addOrUpdateProfile(ConnectionProfile profile) {
        List<ConnectionProfile> profiles = loadProfiles();
        profiles.removeIf(p -> p.getName().equals(profile.getName()));
        profiles.add(profile);
        saveProfiles(profiles);
    }

    public void deleteProfile(String name) {
        List<ConnectionProfile> profiles = loadProfiles();
        profiles.removeIf(p -> p.getName().equals(name));
        saveProfiles(profiles);
    }
}
