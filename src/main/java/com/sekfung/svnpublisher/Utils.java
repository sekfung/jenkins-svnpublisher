package com.sekfung.svnpublisher;

import hudson.EnvVars;
import hudson.FilePath;
import org.springframework.util.StringUtils;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNStatus;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * @author sekfung
 */
public class Utils {
    private static final Logger LOGGER = Logger.getLogger(Utils.class.getName());
    private static final Pattern ENV_REGEX = Pattern.compile("\\$\\{.+}");

    public static EnvVars appendSvnEnv(EnvVars vars, File workingCopy)  {
        try {
            SVNStatus status = SVNClientManager.newInstance().getStatusClient().doStatus(workingCopy, false);
            vars.put("SVN_REVISION", String.valueOf(status.getRevision().getNumber()));
            vars.put("SVN_URL", status.getRepositoryRootURL().toDecodedString());
        } catch (SVNException e) {
            e.printStackTrace();
        }
        return vars;
    }



    public static String replaceVars(EnvVars vars, String original) {
        String replaced = original;
        if (ENV_REGEX.matcher(original).matches()) {
            for (Map.Entry<String, String> k : vars.entrySet()) {
                Pattern p = Pattern.compile("\\$\\{" + k.getKey() + "}");
                Matcher m = p.matcher(replaced);
                if (m.find()) {
                    replaced = m.replaceAll(vars.get(k.getKey()).trim());
                }
            }
        }

        return replaced;
    }

    /**
     * Replace the env vars and parameters of jenkins in
     *
     * @param <T>
     * @param vars
     * @param originalArtifacts
     * @return
     */
    public static <T> List<T> parseAndReplaceEnvVars(EnvVars vars, List<T> originalArtifacts) {
        for (T a : originalArtifacts) {
            Field[] fields;
            fields = a.getClass().getDeclaredFields();
            for (Field f : fields) {
                if (f.getType().isInstance("")) {
                    String capitalName = StringUtils.capitalize(f.getName());
                    try {
                        Method invokeSet = a.getClass().getDeclaredMethod("set" + capitalName, String.class);
                        Method invokeGet = a.getClass().getDeclaredMethod("get" + capitalName);

                        invokeSet.invoke(a, Utils.replaceVars(vars, (String) invokeGet.invoke(a)));

                    } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NullPointerException ex) {
                        LOGGER.log(Level.FINEST, "{0} {1}", new Object[]{f.getName(), ex.getMessage()});
                    }
                }
            }
        }
        return originalArtifacts;
    }

    public static List<File> findFilesWithPattern(FilePath filePath, String filePattern, String[] params, EnvVars envVars) throws SVNPublisherException {
        try {
            if (!filePath.exists()) {
                throw new IOException("Path does not exists : " + filePath.getRemote());
            }
            boolean canUpload = true;
            for (String variable : params) {
                // empty param value means all files will be upload
                if ("".equals(variable)) {
                    break;
                }
                // invalid param
                if (!variable.contains("=") || variable.split("=").length != 2) {
                    canUpload = false;
                    break;
                } else {
                    String key = variable.split("=")[0];
                    String value = variable.split("=")[1];
                    if (value == null || !value.equals(envVars.get(key))) {
                        canUpload = false;
                        break;
                    }
                }
            }
            if (canUpload) {
                ListFiles listFiles = new ListFiles(filePattern, "");
                Map<String, String> files = filePath.act(listFiles);
                return files.values().stream().map(File::new).collect(Collectors.toList());
            }
            return new ArrayList<>();
        } catch (PatternSyntaxException e) {
            throw new SVNPublisherException("Invalid pattern file for " + filePattern);
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            throw new SVNPublisherException("Invalid pattern file for " + e.getMessage());
        }
    }

}
