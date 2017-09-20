package vin.evan;

import edu.jhuapl.dorset.agents.AgentRequest;


public class Main {

	public static void main(String[] args) {
		String uri = "JIRA_URI";
		String user = "JIRA_TICKETING_USER";
		String pass = "JIRA_TICKETING_USER_PASSWORD";
		
		JiraAgent agent = new JiraAgent(uri, user, pass);
		
		AgentRequest request = new AgentRequest();
		request.setText("Project Waffle User Evan");
		request.setText("Create issue Test Jira Client Ticket Jira Project Waffle Assign Evan");
		request.setText("Resolve issue 1086 Incomplete Jira Project Waffle");
		request.setText("Add comment 1086 This is a comment Jira Project Waffle");
		request.setText("Assign Issue 1086 Jira Project Waffle Assign Evan");
		agent.process(request);

	}

}
