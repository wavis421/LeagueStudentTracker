package controller;

import java.util.ArrayList;
import java.util.prefs.Preferences;

import org.joda.time.DateTime;

import model.ActivityEventModel;
import model.LogDataModel;
import model.MySqlDatabase;
import model.StudentImportModel;
import model.StudentNameModel;

public class StudentTracker {
	// Different port than League Data Manager to allow simultaneous connects
	private static final int LOCAL_SSH_PORT = 6000;

	private MySqlDatabase sqlDb;

	public static void main(String[] args) {
		new StudentTracker();
	}

	public StudentTracker() {
		// Import data starting 4 days ago
		DateTime startDate = new DateTime().minusDays(4);
		String startDateString = startDate.toString().substring(0, 10);

		// Retrieve tokens and passwords
		Preferences prefs = Preferences.userRoot();
		String githubToken = prefs.get("GithubToken", "");
		String pike13Token = prefs.get("Pike13Token", "");
		String awsPassword = prefs.get("AWSPassword", "");

		// Connect to database
		sqlDb = new MySqlDatabase(awsPassword, LOCAL_SSH_PORT);
		if (!sqlDb.connectDatabase()) {
			// TODO: Handle this error
			System.exit(0);
		}

		// Import all the League databases
		Pike13ApiController pike13Controller = new Pike13ApiController(sqlDb, pike13Token);
		importStudentsFromPike13(pike13Controller);
		importActivitiesFromPike13(startDateString, pike13Controller);

		GitApiController gitController = new GitApiController(sqlDb, githubToken);
		importGithubComments(startDateString, gitController);

		sqlDb.disconnectDatabase();
		System.exit(0);
	}

	public void importStudentsFromPike13(Pike13ApiController pike13Controller) {
		sqlDb.insertLogData(LogDataModel.STARTING_STUDENT_IMPORT, new StudentNameModel("", "", false), 0, " ***");

		// Get data from Pike13
		ArrayList<StudentImportModel> studentList = pike13Controller.getClients();

		// Update changes in database
		if (studentList.size() > 0)
			sqlDb.importStudents(studentList);

		sqlDb.insertLogData(LogDataModel.STUDENT_IMPORT_COMPLETE, new StudentNameModel("", "", false), 0, " ***");
	}

	public void importActivitiesFromPike13(String startDate, Pike13ApiController pike13Controller) {
		sqlDb.insertLogData(LogDataModel.STARTING_ATTENDANCE_IMPORT, new StudentNameModel("", "", false), 0,
				" starting after " + startDate + " ***");

		// Get data from Pike13
		ArrayList<ActivityEventModel> eventList = pike13Controller.getEnrollment(startDate);

		// Update changes in database
		if (eventList.size() > 0)
			sqlDb.importActivities(eventList);

		sqlDb.insertLogData(LogDataModel.ATTENDANCE_IMPORT_COMPLETE, new StudentNameModel("", "", false), 0, " ***");
	}

	public void importGithubComments(String startDate, GitApiController gitController) {
		boolean result;
		sqlDb.insertLogData(LogDataModel.STARTING_GITHUB_IMPORT, new StudentNameModel("", "", false), 0,
				" starting after " + startDate + " ***");

		result = gitController.importGithubComments(startDate);
		if (result)
			gitController.importGithubCommentsByLevel(0, startDate);

		if (result)
			sqlDb.insertLogData(LogDataModel.GITHUB_IMPORT_COMPLETE, new StudentNameModel("", "", false), 0, " ***");
		else
			sqlDb.insertLogData(LogDataModel.GITHUB_IMPORT_ABORTED, new StudentNameModel("", "", false), 0,
					": Github API rate limit exceeded ***");
	}
}
