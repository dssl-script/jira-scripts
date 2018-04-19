with open("work_shedule.txt", "r") as f:
	text = f.readlines()

print text[0].split("\t")
