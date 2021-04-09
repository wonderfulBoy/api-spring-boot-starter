package com.github.api.arch;

import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Determine if the current project is under Git management,
 * and if so, add the API document to Git management
 *
 * @author echils
 * @since 2021-02-27 18:43:18
 */
class GitRepositoryManager {

    private static final Logger logger = LoggerFactory.getLogger(GitRepositoryManager.class);

    /**
     * Git manager
     */
    private Git manager;

    /**
     * Project absolute path
     */
    private String projectPath;


    GitRepositoryManager(String projectPath) {
        this.projectPath = projectPath;
    }

    /**
     * Determine whether the current project is a Git project
     *
     * @return
     */
    boolean isGitProject() {
        try {
            if (manager == null) {
                manager = Git.open(new File(projectPath));
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    /**
     * Add the file to Git management
     *
     * @param fileRelativePath The relative path of the submitted file will be pushed
     */
    void addFile(String fileRelativePath) {
        try {
            if (manager == null) {
                manager = Git.open(new File(projectPath));
            }
            manager.add().addFilepattern(fileRelativePath).call();
        } catch (Exception e) {
            logger.error("Git manager failed:{}", e.getMessage());
            throw new GitOperateException(e);
        }
    }

}
