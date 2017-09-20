package vin.evan;

import java.util.List;

import net.rcarz.jiraclient.BasicCredentials;
import net.rcarz.jiraclient.Field;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.Project;
import net.rcarz.jiraclient.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.jhuapl.dorset.ResponseStatus;
import edu.jhuapl.dorset.agents.AbstractAgent;
import edu.jhuapl.dorset.agents.AgentRequest;
import edu.jhuapl.dorset.agents.AgentResponse;
import edu.jhuapl.dorset.agents.Description;

public class JiraAgent extends AbstractAgent{
	
	private final Logger logger = LoggerFactory.getLogger(JiraAgent.class);
    
    private static final String SUMMARY = "Create issues, assign issues to users, close issues, comment issues, etc.";
    private static final String EXAMPLE = "Create issue Fix header file for website, Jira Project Testing Suite";
    
    private static final String[] TASKS = new String[]{"create issue", "resolve issue", "add comment", "assign issue"};
    private static final String[] RESOLVE = new String[]{"fixed", "duplicate", "incomplete", "done"};
    
    private final static String JIRA_ISSUE_BUG = "Bug";

    private String jiraUri;
    private BasicCredentials creds;
    private JiraClient jira;

    
    /**
     * Create a JIRA agent
     *
     * @param jiraUri  the URI to connect to users JIRA account
     * @param username  username for user that will manage agents requests
     * @param password  password for user that will manage agents requests
     */
    public JiraAgent(String jiraUri, String username, String password) {
        this.jiraUri = jiraUri;
        creds = new BasicCredentials(username, password);
        this.setDescription(new Description("jira", SUMMARY, EXAMPLE));
    }
    
    
	public AgentResponse process(AgentRequest request) {
		logger.debug("Handling the request: " + request.getText());
		
		try {
			jira = new JiraClient(jiraUri, creds);
		} catch (JiraException e) {
			e.printStackTrace();
		}
		
		String agentRequest = request.getText().toLowerCase();

		        
        logger.debug("Finding project to attach task to...");
        String project = findProject(agentRequest);
        if(project.equals("")){
        	return new AgentResponse(ResponseStatus.Code.AGENT_DID_NOT_UNDERSTAND_REQUEST);
        }
        
        
        logger.debug("Finding task to complete..");
        Integer task = findTask(agentRequest);
        if(task == -1){
        	return new AgentResponse(ResponseStatus.Code.AGENT_DID_NOT_UNDERSTAND_REQUEST);
        }
        
        
        logger.debug("Finding assignee if stated...");
        String assignee = findAssignee(agentRequest, project);
        if(assignee.equals("")){
        	assignee = creds.getLogonName();
        }
        
        return handleTask(task, project, agentRequest, assignee);
	}
	
	protected String findAssignee(String agentRequest, String project) {
		String assignee = "";
		int assignIndex = agentRequest.indexOf(" assign");
		if(assignIndex != -1){
			String foundAssignee = agentRequest.substring(assignIndex).replaceAll(" assign ","");
			try {
				List<User> users = User.getAll(jira.getRestClient(), project);
				for(User u : users){
					if(u.getDisplayName().toLowerCase().indexOf(foundAssignee) != -1 || 
							u.getName().toLowerCase().indexOf(foundAssignee) != -1){
						assignee = u.getName();
						break;
					}
				}					
			} catch (JiraException e) {
				return "";
			}
		}
		return assignee;
	}


	protected AgentResponse handleTask(Integer task, String project, String agentRequest, String assignee) {
		AgentResponse response = null;
		switch(task){
			case 0:
				//Create Issue
				response = createIssue(agentRequest, project, assignee);
				break;
			case 1:
				//Resolve Issue
				response = resolveIssue(agentRequest, project);
				break;
			case 2:
				//Add Comment
				response = addComment(agentRequest, project);
				break;
			case 3:
				response = assignIssue(agentRequest, project, assignee);
				break;
			default:
				break;
		}
		
		return response;
	}


	protected Integer findTask(String agentRequest) {
		Integer task = -1;		
		for(int i = 0; i < TASKS.length; i++){
			if(agentRequest.indexOf(TASKS[i]) != -1){
				task = i;
				break;
			}
		}
		return task;
	}
	
	
	protected String findProject(String agentRequest){
		String project = "";
		int projectIndex = agentRequest.indexOf("jira project");
		if(projectIndex != -1){
			String foundProject = agentRequest.substring(projectIndex).replaceAll("jira project ","");
			if(foundProject.indexOf("assign") != -1){
				foundProject = foundProject.substring(0,foundProject.indexOf("assign")-1);
			}
			try {
				List<Project> projects = jira.getProjects();
				for(Project p : projects){
					if(p.getKey().toLowerCase().indexOf(foundProject) != -1 || 
							p.getName().toLowerCase().indexOf(foundProject) != -1){
						project = p.getKey();
						break;
					}
				}					
			} catch (JiraException e) {
				return "";
			}
		}	
		
		return project;
	}
	
	
	protected AgentResponse createIssue(String agentRequest, String project, String assignee){
		String summary = agentRequest.substring(0, agentRequest.indexOf("jira project")-1).replaceAll("create issue ", "");
		Issue issue = null;
		try {
			issue = jira.createIssue(project, JIRA_ISSUE_BUG)
					.field(Field.SUMMARY, summary)
					.field(Field.ASSIGNEE, assignee)
					.execute();
		} catch (JiraException e) {
			return new AgentResponse(ResponseStatus.Code.AGENT_INTERNAL_ERROR);
		}
		return new AgentResponse("Issue " + project + "-" + issue.getKey() + " created.");
	}
	
	protected AgentResponse resolveIssue(String agentRequest, String project) {
		String text = agentRequest.substring(0, agentRequest.indexOf("jira project")-1).replaceAll("resolve issue ", "");
		String key = project + "-" + text.replaceAll("[^0-9].*", "");
		String resolution = text.replaceAll("^[0-9]*\\s","");
		resolution = resolution.substring(0,1).toUpperCase() + resolution.substring(1);
		
		Issue issue = null;
		try {
			//TODO: Issue transition resolve doesn't really work like it should. 
			//		Current rcarz version is not maintained enough.
			issue = jira.getIssue(key);
			
			issue.addComment(resolution);
			
			if(resolution.equals("Fixed") || resolution.equals("Done")){
				issue.transition()
	            .execute("Resolve Issue");
			}
			else{
				issue.transition()
	            .execute("Close Issue");
			}
		} catch (JiraException e) {
			e.printStackTrace();
			return new AgentResponse(ResponseStatus.Code.AGENT_INTERNAL_ERROR);
		}
		return new AgentResponse("Issue " + key + " resolved to " + resolution + ".");
	}
	
	protected AgentResponse addComment(String agentRequest, String project) {
		String text = agentRequest.substring(0, agentRequest.indexOf("jira project")-1).replaceAll("add comment ", "");
		String key = project + "-" + text.replaceAll("[^0-9].*", "");
		String comment = text.replaceAll("^[0-9]*\\s","");
		Issue issue = null;
		try {
			issue = jira.getIssue(key);
			issue.addComment(comment);
		} catch (JiraException e) {
			return new AgentResponse(ResponseStatus.Code.AGENT_INTERNAL_ERROR);
		}
		return new AgentResponse("Comment '" + comment + "' added to " + project + "-" + issue.getKey() + ".");
	}
	
	protected AgentResponse assignIssue(String agentRequest, String project, String assignee) {
		String text = agentRequest.substring(0, agentRequest.indexOf("jira project")-1).replaceAll("assign issue ", "");
		String key = project + "-" + text.replaceAll("[^0-9].*", "");
		Issue issue = null;
		try {
			issue = jira.getIssue(key);
			issue.update()
				.field(Field.ASSIGNEE, assignee)
				.execute();
		} catch (JiraException e) {
			return new AgentResponse(ResponseStatus.Code.AGENT_INTERNAL_ERROR);
		}
		return new AgentResponse("Issue " + key + " reassigned to " + assignee + ".");
	}
	

}
