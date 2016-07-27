package hudson.plugins.jigomerge;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.tasks.Builder;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

public class JigomergeBuilder extends Builder {

	@Extension
	public static final JigomergeBuildDescriptor DESCRIPTOR = new JigomergeBuildDescriptor();

	private static final String JIGOMERGE_VERSION = "2.2.6";
	private static final String JIGOMERGE_CLASSNAME = "SvnMergeTool";

	private final String source;
	private final String username;
	private final String password;
	private final boolean oneByOne;
	private final boolean eager;
	private boolean dryRun;
	private boolean verbose;
	private final String validationScript;
	private final List<String> ignoreMergePatterns;
	private String commitCommentPrefix;

	@DataBoundConstructor
	public JigomergeBuilder(String source, String username, String password,
			boolean oneByOne, boolean eager, String validationScript,
			boolean dryRun, boolean verbose, String ignoreMergePatterns, String commitCommentPrefix) {
		this.source = source;
		this.username = username;
		this.password = password;
		this.oneByOne = oneByOne;
		this.eager = eager;
		this.dryRun = dryRun;
		this.verbose = verbose;

		if (StringUtils.isNotEmpty(validationScript)) {
			this.validationScript = validationScript;
		} else {
			this.validationScript = null;
		}

		this.ignoreMergePatterns = new ArrayList<String>();
		if (StringUtils.isNotEmpty(ignoreMergePatterns)) {
			StringTokenizer tokenizer = new StringTokenizer(ignoreMergePatterns, ",");
			while (tokenizer.hasMoreTokens()) {
				this.ignoreMergePatterns.add(tokenizer.nextToken());
			}
		}
		
		this.commitCommentPrefix = "";
		if(StringUtils.isNotEmpty(commitCommentPrefix)){
			this.commitCommentPrefix = commitCommentPrefix;
		}
	}

	@Override
	public Descriptor<Builder> getDescriptor() {
		return DESCRIPTOR;
	}

	@Override
	public boolean perform(final AbstractBuild<?, ?> build,
			final Launcher launcher, final BuildListener listener)
			throws InterruptedException, IOException {
		String workingDirectory = build.getModuleRoot().toURI().getPath();

		MergeResult result = new MergeResult();
		result.setStatus(false);

		try {
			InputStream scriptResource = this.getClass().getResourceAsStream(
					"/scripts/jigomerge-" + JIGOMERGE_VERSION + ".groovy");
			GroovyClassLoader gcl = new GroovyClassLoader();
			Class<?> clazz = gcl.parseClass(scriptResource, JIGOMERGE_CLASSNAME);
			Constructor<?>[] constructors = clazz.getConstructors();
			GroovyObject instance = (GroovyObject) constructors[0].newInstance(
					dryRun, ignoreMergePatterns, oneByOne, eager, verbose,
					username, password, listener.getLogger(), commitCommentPrefix);

			Object[] mergeArgs = { source, validationScript,
					"" + workingDirectory + "." };
			Map returnedObject = (Map) instance.invokeMethod("launchSvnMerge",
					mergeArgs);
			listener.getLogger().println("return : " + returnedObject);

			// fill merge result
			result.setStatus((Boolean) returnedObject.get("status"));
			List<String> conflictingRevisions = (List<String>) returnedObject
					.get("conflictingRevisions");
			if (conflictingRevisions != null && !conflictingRevisions.isEmpty()) {
				result.getConflictingRevisions().addAll(conflictingRevisions);
				build.setResult(Result.UNSTABLE);
			}

		} catch (Exception e) {
			listener.getLogger().println(e.getClass() + " # " + e.getMessage());
			build.setResult(Result.FAILURE);
			e.printStackTrace(listener.getLogger());
		}

		Action action = new JigomergeBuildAction(build, result, listener);
		build.addAction(action);

		return true;
	}

	public String getSource() {
		return source;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public boolean isOneByOne() {
		return oneByOne;
	}

	public boolean isEager() {
		return eager;
	}

	public boolean isDryRun() {
		return dryRun;
	}

	public boolean isVerbose() {
		return verbose;
	}

	public String getValidationScript() {
		return validationScript;
	}

	public String getIgnoreMergePatterns() {
		return StringUtils.join(ignoreMergePatterns.toArray(), ",");
	}

	public String getCommitCommentPrefix() {
		return commitCommentPrefix;
	}

}
