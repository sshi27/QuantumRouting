# Repo for *Concurrent Entanglement Routing for Quantum Networks: Model and Designs*

## Abstract

Quantum entanglement enables a number of important applications such as quantum key distribution. Based on quantum entanglement, quantum networks are built to enable long-distance secret sharing between two remote communication parties. Establishing a multi-hop quantum entanglement exhibits a high failure rate and existing quantum networks rely on trusted repeater nodes to transmit quantum bits. However, when the scale of a quantum network increases, it is required to have end-to-end multi-hop quantum entanglements in order to deliver secret bits without letting the repeaters know the secret bits. This work focuses on the entanglement routing problem, whose objective is to build long-distance entanglements for the concurrent source-destination pairs through multiple hops. Different from existing works that analyzes the traditional routing techniques on special network topologies, we present a comprehensive entanglement routing model that reflects the differences between quantum networks and classical networks and a new entanglement routing algorithm that utilizes the unique properties of quantum networks. Evaluation results show that the proposed algorithm **Q-CAST** increases the number of successful long-distance entanglements by a big margin compared to other methods. The model and simulator developed by this work may encourage more network researchers to study the entanglement routing problem.

Our paper will appear in ACM SIGCOMM 2020.  

**Paper Link:** https://users.soe.ucsc.edu/~qian/papers/QuantumRouting.pdf

## Repository Structure

- `src/main/kotlin`         source code root. **Under this directory:**
- `utils`                   utilities functions
- `quantum/algorithm`       quantum routing algorithms, including Q-PASS, Q-CAST, SLMP, Greedy, and more
- `quantum/topo`            abstractions for components in a quantum network: links, nodes, and the quantum network
- `quantum/Visualizer.kt`   a graphical interface for easy algorithm debugging, comprehending, and improving. 
- `quantum/Plot.kt`         generates all simulation figure data
- `quantum/Analytical.kt`   generates all analytical figure data


## Hardware and Software requirements. 

Hardware requirements: 
A commodity server or a virtual/cloud/hosted server with the hardware:
  - Internet access
  - 16GiB RAM
  - A powerful CPU, e.g., Intel(R) Core(TM) i7-7700 CPU @ 3.60GHz
  - 1.8GiB HDD/SSD available for logging and plotting

Software requirements: 
  - Any OS with lasted Docker and Git installed. For completeness, we put the installation steps later in the PS. 

## Run demo with GUI

```bash
# 1. *This step is platform-specific* 
# Install Java >= 8, maven >= 3.6, Python >= 3.8, for simulation, and install texlive and fonts [linuxlibertine & times new roman] for figure plotting. 
# For Ubuntu >= 20.04
apt install maven python3 python3-pip texlive fonts-linuxlibertine ttf-mscorefonts-installer -y
# if you use other OSes: please try installing by yourself, or try our docker image without GUI. No influences on the final simulation results. 

# 2. Download source codes
git clone https://github.com/sshi27/QuantumRouting
git clone https://github.com/sshi27/plot

# 3. Run the plot daemon
cd plot
python3.8 -m pip install -r requirements.txt
python3.8 file-watcher.py &

# 4. Run simulations
cd ../QuantumRouting
mvn compile
mvn exec:java -D"exec.mainClass"="quantum.Main"
# A GUI window showing the routing process should appear, if a window system is properly set. 
# Or a warning should appear, input `Y` and press [Enter] to continue. 

# 5. Plot figures in our paper
mvn exec:java -D"exec.mainClass"="quantum.AnalyticalPlot"
mvn exec:java -D"exec.mainClass"="quantum.Plot"

# 6. Figures are under ../plot/dist
```

## Run demo in Docker and without GUI

```bash
# 0. Download source codes
git clone https://github.com/sshi27/QuantumRouting
cd QuantumRouting

# 1. run container
docker build -t quantum_routing . && docker run -it --rm quantum_routing bash
# This takes some time (typically < 10min), subject to your Internet bandwidth and processing power of your machine. 
# In this step, a brand new minimal Ubuntu 20.04 is pulled as the base docker image,
# and our simulator and figure plotter are pulled from the Internet and initialized. 

# 2. Run the plot daemon
cd plot
python3 file-watcher.py &

# 3. Run all simulations
cd ../QuantumRouting
mvn exec:java -D"exec.mainClass"="quantum.Main"
 # A warning should appear, input `Y` and press [Enter]

# 4. Plot all experimental figures in our paper
mvn exec:java -D"exec.mainClass"="quantum.AnalyticalPlot"
mvn exec:java -D"exec.mainClass"="quantum.Plot"

# 5. Figures are under ../plot/dist
```

## Hacking the code
Each algorithm under `quantum/algorithm` directory is an individual algorithm, or a set of similar algorithms.
Basically, you create a new routing algorithm in two steps: 
1) Extend class `quantum.algorithm` or its existing subclasses
2) Define the behaviors of a routing algorithm in P2 and P4, by implementing the three abstract methods: 
```kotlin
  abstract fun prepare()
  abstract fun P2()
  abstract fun P4()
```

You can refer to "GreedyHopRouting.kt" as a simple start point.

## Plot with partial logging data
A whole simulation round takes typically ~ 1 day on a powerful multi-core CPU (e.g. i7-7700), and each pass takes about 2 GiB of disk space for logging. 
The current number of rounds is set to 1, which is enough for all simulation results and graph plotting. You can change the number of rounds at `src\main\kotlin\quantum\configs.kt:21`

During a multi-round simulation, you can stop the simulation and still get the complete set of graphs, if a single round is completed (when a file `dist/50#2-0.9-3-0.6-6-10-CR.txt` exists). You can even stop anywhere and run plotting based on the current partial logs. Some points on the figures may be missing, though. 


Detailed steps: 
0. Keep the simulation running, just in case. And open another terminal, or press ctrl+z to halt the simulation without terminating it. 

1. Make sure the plotting script is running
```bash
ps aux | grep file-watcher
```
2. If something like "python file-watcher.py" is shown, then skip this step. Else, please run
```bash
cd plot
python3 file-watcher.py &
```

3. Continue in the previous terminal: 
```bash
cd ../QuantumRouting
mvn exec:java -D"exec.mainClass"="quantum.Plot"
```

4. Find generated figures. 
```bash
cd ../plot/dist && ls
```


## Authors

- Shouqian Shi(sshi27@ucsc.edu)
- Chen Qian(cqian12@ucsc.edu)