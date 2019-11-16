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

import constraintOptimizer.ConstraintOptimizer.OptimizerConfiguration;
import scioly.CompleteTeamRoster;
import scioly.Team;
import scioly.Team.TeamMember;
import scioly.TeamRosterConfiguration;
import scioly.Tournament;
import scioly.Tournament.ScheduleException;
import scioly.Tournament.TournamentEvent;

public class Main {

	public static void main(String[] args) throws ScheduleException, URISyntaxException, IOException {
		System.out.println(" (           (        )   (        )                        )                    (    (    (                )       (     \r\n" +
				" )\\ )   (    )\\ )  ( /(   )\\ )  ( /(                     ( /(   *   )     (      )\\ ) )\\ ) )\\ )  (       ( /(       )\\ )  \r\n" +
				"(()/(   )\\  (()/(  )\\()) (()/(  )\\())  (    (   (   (    )\\())` )  /(     )\\    (()/((()/((()/(  )\\ )    )\\()) (   (()/(  \r\n" +
				" /(_))(((_)  /(_))((_)\\   /(_))((_)\\   )\\   )\\  )\\  )\\  ((_)\\  ( )(_)) ((((_)(   /(_))/(_))/(_))(()/(   ((_)\\  )\\   /(_)) \r\n" +
				"(_))  )\\___ (_))    ((_) (_)) __ ((_) ((_) ((_)((_)((_)  _((_)(_(_())   )\\ _ )\\ (_)) (_)) (_))   /(_))_  _((_)((_) (_))   \r\n" +
				"/ __|((/ __||_ _|  / _ \\ | |  \\ \\ / / | __|\\ \\ / / | __|| \\| ||_   _|   (_)_\\(_)/ __|/ __||_ _| (_)) __|| \\| || __|| _ \\  \r\n" +
				"\\__ \\ | (__  | |  | (_) || |__ \\ V /  | _|  \\ V /  | _| | .` |  | |      / _ \\  \\__ \\\\__ \\ | |    | (_ || .` || _| |   /  \r\n" +
				"|___/  \\___||___|  \\___/ |____| |_|   |___|  \\_/   |___||_|\\_|  |_|     /_/ \\_\\ |___/|___/|___|    \\___||_|\\_||___||_|_\\  \r\n" +
				"                                                                                                                          ");

		Scanner scanner = new Scanner(System.in);
		String path = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
		Config configuration = parseConfig(new FileInputStream(path + "/config.txt"));

		System.out.println("\noptimize? (Y/n)");
		String s = scanner.nextLine();
		if (!s.equals("") && !s.toLowerCase().equals("y"))
			System.exit(0);

		System.out.println("optimizing...");
		HashSet<CompleteTeamRoster> rosters = new ConstraintOptimizer(configuration.getOptimizerConfiguration(), configuration.getTeamRosterConfiguration()).optimize();

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

	public static Config parseConfig(InputStream is) throws IOException {
		Team team = new Team();
		int[] targets = null;
		ArrayList<Integer> timeslots = new ArrayList<Integer>();
		Tournament tournament = null;
		ArrayList<TournamentEvent> building = new ArrayList<TournamentEvent>();
		ArrayList<TeamMember[]> stacks = new ArrayList<TeamMember[]>();
		ArrayList<TeamMember[]> unstacks = new ArrayList<TeamMember[]>();

		int threads = 4;
		int tolerance1 = 0;
		int tolerance2 = 0;

		ArrayList<String> stages = new ArrayList<String>(Arrays.asList(new String[] {
				"config",
				"timeslots",
				"team",
				"building",
				"schedule",
				"stack"
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

			if (stage.equals("config")) {
				if (line.split(" : ")[0].equals("teams")) {
					String[] ss = line.split(" : ")[1].split(", ");
					targets = new int[ss.length];
					for (int i = 0; i < ss.length; i++) {
						try {
							targets[i] = Integer.parseInt(ss[i]);
						} catch (NumberFormatException e) {
							System.err.println("'" + ss[i] + "' is not a number (line " + lineNum + ")");
							System.exit(1);
						}
					}
				}
				else if (line.split(" : ")[0].equals("threads")) {
					try {
						threads = Integer.parseInt(line.split(" : ")[1]);
					} catch (NumberFormatException e) {
						System.err.println("'" + line.split(" : ")[1] + "' is not a number (line " + lineNum + ")");
						System.exit(1);
					}
				}
				else if (line.split(" : ")[0].equals("tolerances")) {
					String s = line.split(" : ")[1];
					if (s.split(", ").length != 2) {
						System.err.println("Expected 2 numbers separated by ', ' on line " + lineNum);
						System.exit(1);
					}
					try {
						tolerance1 = Integer.parseInt(s.split(", ")[0]);
						tolerance2 = Integer.parseInt(s.split(", ")[1]);
					} catch (NumberFormatException e) {
						System.err.println("'" + s + "' is not a number (line " + lineNum + ")");
						System.exit(1);
					}
				}
			}
			else if (stage.equals("timeslots")) {
				for (String s : line.split(" : ")) {
					try {
						timeslots.add(Integer.parseInt(s));
					} catch (NumberFormatException e) {
						System.err.println("'" + s + "' is not a number (line " + lineNum + ")");
						System.exit(1);
					}
				}
			}
			else if (stage.equals("schedule")) {
				if (tournament == null) {
					System.err.println("Section 'timeslots' must be before section 'schedule'");
					System.exit(1);
					continue;
				}

				String event = line.split(" : ")[0];
				if (line.split(" : ").length < 2) {
					System.err.println("expected ' : ' (line " + lineNum + ")");
					System.exit(1);
				}
				int timeslot = 0;
				try {
					timeslot = Integer.parseInt(line.split(" : ")[1]);
				} catch (NumberFormatException e) {
					System.err.println("'" + line.split(" : ")[1] + "' is not a number (line " + lineNum + ")");
					System.exit(1);
				}
				if (line.split(" : ").length > 2){
					try {
						tournament.addEvent(event, timeslot, Integer.parseInt(line.split(" : ")[2]));
					} catch (NumberFormatException e) {
						e.printStackTrace();
					} catch (ScheduleException e) {
						e.printStackTrace();
					}
				}
				else {
					try {
						tournament.addEvent(event, timeslot, 2);
					} catch (ScheduleException e) {
						e.printStackTrace();
					}
				}
			}
			else if (stage.equals("team")) {
				if (tournament == null) {
					System.err.println("team section before timeslots section");
					continue;
				}

				String name = line.split(" : ")[0];
				if (line.split(" : ").length < 2) {
					System.err.println("expected ' : ' (line " + lineNum + ")");
					System.exit(1);
				}
				String[] events = line.split(" : ")[1].split(", ");
				ArrayList<TournamentEvent> eventList = new ArrayList<TournamentEvent>();
				for (String event : events) {
					if (tournament.getEvent(event) == null) {
						System.err.println("event " + event + " does not exist (line " + lineNum + ")");
						System.exit(1);
					}
					eventList.add(tournament.getEvent(event));
				}
				team.addTeamMember(name, eventList);
			} else if (stage.equals("building")){
				if (tournament.getEvent(line) == null) {
					System.err.println("event " + line + " does not exist (line " + lineNum + ")");
					System.exit(1);
				}
				tournament.getEvent(line).setBuilding(true);
				building.add(tournament.getEvent(line));
			} else if (stage.equals("stack")) {
				if (line.contains(" + ")){
					String[] names = line.split(" \\+ ");
					for (String n : names) {
						if (team.getTeamMember(n) == null) {
							System.err.println("team member " + n + " does not exist (line " + lineNum + ")");
							System.exit(1);
						}
					}
					stacks.add(new TeamMember[] {team.getTeamMember(names[0]), team.getTeamMember(names[1])});
				} else if (line.contains(" - ")){
					String[] names = line.split(" - ");
					for (String n : names) {
						if (team.getTeamMember(n) == null) {
							System.err.println("team member " + n + " does not exist");
							System.exit(1);
						}
					}
					unstacks.add(new TeamMember[] {team.getTeamMember(names[0]), team.getTeamMember(names[1])});
				} else {
					System.err.println("Expected ' + ' (stack) or ' - ' (unstack)\nencountered '" + line + "' (line " + lineNum + ")\nexiting...");
					System.exit(0);
				}
			}
		}

		if (!stage.equals("")) {
			System.err.println("Expected 'END SECTION' on line " + lineNum);
			System.exit(1);
		}

		if (targets == null) {
			System.err.println("Missing team sizes in config section");
			System.exit(1);
		}

		TeamRosterConfiguration teamConfig = new TeamRosterConfiguration(team, tournament, targets);
		for (TeamMember[] members : stacks)
			teamConfig.addStack(members[0], members[1]);
		for (TeamMember[] members : unstacks)
			teamConfig.addUnstack(members[0], members[1]);

		OptimizerConfiguration optConfig = new OptimizerConfiguration(threads, tolerance1, tolerance2);

		System.out.println(String.format("parsed config file: \n\t%d blocks\n\t%d events (%d building)\n\t%d team members\n\t%d stacking rules\n\t%d unstacking rules",
				timeslots.size(),
				teamConfig.getTournament().getEvents().size(), building.size(),
				teamConfig.getTeam().getTeamMembers().size(),
				stacks.size(), unstacks.size()));
		System.out.print("target teams: ");
		for (int i : targets)
			System.out.print(i + " ");
		System.out.println();
		System.out.println(String.format("using %d threads, tolerances %d, %d", threads, tolerance1, tolerance2));

		return new Config(optConfig, teamConfig);
	}

	private static class Config {

		private OptimizerConfiguration opt;
		private TeamRosterConfiguration team;

		private Config(OptimizerConfiguration opt, TeamRosterConfiguration team) {
			this.opt = opt;
			this.team = team;
		}

		private OptimizerConfiguration getOptimizerConfiguration() {
			return opt;
		}

		private TeamRosterConfiguration getTeamRosterConfiguration() {
			return team;
		}

	}

}
