A = load 'passwd-data' using PigStorage(':');
B = foreach A generate $0 as id;
dump B;
store B into 'id.out';
