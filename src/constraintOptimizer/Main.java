package constraintOptimizer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;

import scioly.CompleteTeamRoster;
import scioly.Team;
import scioly.Team.TeamMember;
import scioly.TeamRosterConfiguration;
import scioly.Tournament;
import scioly.Tournament.ScheduleException;
import scioly.Tournament.TournamentEvent;

public class Main {

	public static void main(String[] args) throws ScheduleException, URISyntaxException, IOException {
		Scanner scanner = new Scanner(System.in);
		String path = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
		TeamRosterConfiguration configuration = parseConfig(new FileInputStream(path + "/config.txt"));

		System.out.println("\noptimize? (Y/n)");
		String s = scanner.nextLine();
		if (!s.equals("") && !s.toLowerCase().equals("y"))
			System.exit(0);

		System.out.println("optimizing...");
		HashSet<CompleteTeamRoster> rosters = new ConstraintOptimizer(configuration).optimize();

		System.out.println("\noutput file? (default 'rosters.csv')");
		s = scanner.nextLine();
		if (s.equals(""))
			s = "rosters.csv";

		System.out.print("exporting... ");
		try (Writer writer = new BufferedWriter(new OutputStreamWriter(
				new FileOutputStream(path + "/" + s), "utf-8"))) {
			for (CompleteTeamRoster roster : rosters) {
				writer.write(roster.csv());
				writer.write("\n");
			}
		}
		System.out.println("done!");
	}

	public static TeamRosterConfiguration parseConfig(InputStream is) throws IOException {
		Team team = new Team();
		ArrayList<Integer> timeslots = new ArrayList<Integer>();
		Tournament tournament = null;
		ArrayList<TeamMember[]> stacks = new ArrayList<TeamMember[]>();
		ArrayList<TeamMember[]> unstacks = new ArrayList<TeamMember[]>();

		ArrayList<String> stages = new ArrayList<String>(Arrays.asList(new String[] {
				"config",
				"timeslots",
				"team",
				"building",
				"schedule",
				"stack",
				"lottery"
		}));
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line;
		String stage = "";
		int lineNum = 0;
		while ((line = br.readLine()) != null){
			lineNum++;
			if (line.equals("") || line.startsWith("#"))
				continue;

			if (stages.contains(line)) {
				if (!stage.equals("")) {
					System.err.println("Expected 'END SECTION' on line " + lineNum);
					System.exit(1);
				}
				stage = line;
				continue;
			}

			if (line.equals("END SECTION")) {
				if (stage.equals("timeslots")) {
					tournament = new Tournament(timeslots);
				}
				stage = "";
				continue;
			}

			if (stage.equals("timeslots")) {
				for (String s : line.split(" : ")) {
					timeslots.add(Integer.parseInt(s));
				}
			}
			else if (stage.equals("schedule")) {
				if (tournament == null) {
					System.err.println("Section 'timeslots' must be before section 'schedule'");
					System.exit(1);
					continue;
				}

				String event = line.split(" : ")[0];
				int timeslot = Integer.parseInt(line.split(" : ")[1]);
				if (line.split(" : ").length > 2){
					try {
						tournament.addEvent(event, timeslot, Integer.parseInt(line.split(" : ")[2]));
					} catch (NumberFormatException e) {
						e.printStackTrace();
					} catch (ScheduleException e) {
						e.printStackTrace();
					}
					System.out.println("Event " + event + " is block " + timeslot + ", with target " + Integer.parseInt(line.split(" : ")[2]) + " people");
				}
				else {
					try {
						tournament.addEvent(event, timeslot, 2);
					} catch (ScheduleException e) {
						e.printStackTrace();
					}
					System.out.println("Event " + event + " is block " + timeslot + ", with target 2 people");
				}
			}
			else if (stage.equals("team")) {
				if (tournament == null) {
					System.err.println("team section before timeslots section");
					continue;
				}

				String name = line.split(" : ")[0];
				String[] events = line.split(" : ")[1].split(", ");
				ArrayList<TournamentEvent> eventList = new ArrayList<TournamentEvent>();
				for (String event : events)
					eventList.add(tournament.getEvent(event));
				team.addTeamMember(name, eventList);
				System.out.println(name + " is doing events: " + String.join(", ", events));
			} else if (stage.equals("building")){
				tournament.getEvent(line).setBuilding(true);
				System.out.println(line + " is a building event");
			} else if (stage.equals("stack")) {
				if (line.contains(" + ")){
					String[] names = line.split(" \\+ ");
					stacks.add(new TeamMember[] {team.getTeamMember(names[0]), team.getTeamMember(names[1])});
					System.out.println(line.split(" \\+ ")[0] + " and " + line.split(" \\+ ")[1] + " will be on the same team");
				} else if (line.contains(" - ")){
					String[] names = line.split(" - ");
					unstacks.add(new TeamMember[] {team.getTeamMember(names[0]), team.getTeamMember(names[1])});
					System.out.println(line.split(" - ")[0] + " and " + line.split(" - ")[1] + " will be on different teams");
				} else {
					System.err.println("Expected ' + ' (stack) or ' - ' (unstack)\nencountered '" + line + "'\nexiting...");
					System.exit(0);
				}
			}
		}

		if (!stage.equals("")) {
			System.err.println("Expected 'END SECTION' on line " + lineNum);
			System.exit(1);
		}

		TeamRosterConfiguration configuration = new TeamRosterConfiguration(team, tournament, new int[] {15, 15});
		for (TeamMember[] members : stacks)
			configuration.addStack(members[0], members[1]);
		for (TeamMember[] members : unstacks)
			configuration.addUnstack(members[0], members[1]);
		return configuration;
	}

}