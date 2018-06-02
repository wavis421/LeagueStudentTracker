package controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.prefs.Preferences;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import model.MySqlDatabase;

public class StudentDataImport {
	private MySqlDatabase sqlDb;

	public static void main(String[] args) {
		new StudentDataImport().importStudentTrackerData();
	}

	public void importStudentTrackerData() {
		// Import data starting 7 days ago
		DateTime today = new DateTime().withZone(DateTimeZone.forID("America/Los_Angeles"));
		String startDateString = today.minusDays(7).toString().substring(0, 10);
		String courseEndDate = today.plusDays(120).toString().substring(0, 10);

		// Retrieve tokens and passwords
		Preferences prefs = Preferences.userRoot();
		String githubToken = prefs.get("GithubToken", "");
		if (githubToken.equals(""))
			githubToken = readFile("./githubToken.txt");
		String pike13Token = prefs.get("Pike13Token", "");
		if (pike13Token.equals(""))
			pike13Token = readFile("./pike13Token.txt");
		String awsPassword = prefs.get("AWSPassword", "");
		if (awsPassword.equals(""))
			awsPassword = readFile("./awsPassword.txt");

		// Connect to database
		sqlDb = new MySqlDatabase(awsPassword, MySqlDatabase.STUDENT_IMPORT_SSH_PORT);
		if (!sqlDb.connectDatabase()) {
			// TODO: Handle this error
			System.out.println("Failed to connect to MySql database");
			System.exit(0);
		}
		
		StudentImportEngine importer = new StudentImportEngine(sqlDb);

		// Connect to Pike13 and import data
		Pike13Api pike13Api = new Pike13Api(sqlDb, pike13Token);
		importer.importStudentsFromPike13(pike13Api);
		importer.importAttendanceFromPike13(startDateString, pike13Api);
		importer.importCourseAttendanceFromPike13(startDateString, courseEndDate, pike13Api);
		importer.importScheduleFromPike13(pike13Api);
		importer.importCoursesFromPike13(pike13Api);

		// Connect to Github and import data
		GithubApi githubApi = new GithubApi(sqlDb, githubToken);
		importer.importGithubComments(startDateString, githubApi);

		sqlDb.disconnectDatabase();
		System.exit(0);
	}

	private String readFile(String filename) {
		try {
			File file = new File(filename);
			FileInputStream fis = new FileInputStream(file);

			byte[] data = new byte[(int) file.length()];
			fis.read(data);
			fis.close();

			return new String(data, "UTF-8");

		} catch (IOException e) {
			// Do nothing if file is not there
		}
		return "";
	}
}
