# Science Olympiad Event Assigner 2
 
A complete rewrite of the original Scioly Event Assigner (was on [Soumil's github](https://github.com/soumilm), no longer available).

The Scioly Event Assigner 2 implements a two-step depth-first branch-and-bound algorithm to optimize event assignments across teams.

## Downloads

see releases

## Usage

Make sure `config.txt` is in the directory containing the .jar file. Modify `config.txt` as needed.

```
java -jar assigner.jar

. . . STUFF . . .

optimize? (Y/n)
y
optimizing...

. . . stuff . . .

output file? (default 'rosters.csv')
[press enter or enter filename]
exporting... done!
```

A list of rosters will be exported to the directory containing the .jar file as a .csv file.

## Credits

Credits to [soumilm](https://github.com/soumilm) for writing the original event assignmer framework and making me reimplement it.
