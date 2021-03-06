package gobblin.data.management.copy.replication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;

import gobblin.util.ClassAliasResolver;
import lombok.Getter;


/**
 * Class ReplicationConfiguration is used to describe the overall configuration of the replication flow for
 * a dataset, including:
 * <ul>
 *  <li>Replication copy mode {@link ReplicationCopyMode}
 *  <li>Meta data {@link ReplicationMetaData}
 *  <li>Replication source {@link EndPoint}
 *  <li>Replication replicas {@link EndPoint}
 *  <li>Replication topology {@link DataFlowTopology}
 * </ul>
 * @author mitu
 *
 */

public class ReplicationConfiguration {
  public static final String REPLICATION_COPY_MODE = "copymode";
  public static final String METADATA = "metadata";
  public static final String METADATA_JIRA = "jira";
  public static final String METADATA_OWNER = "owner";
  public static final String METADATA_NAME = "name";

  public static final String REPLICATION_SOURCE = "source";
  public static final String REPLICATION_REPLICAS = "replicas";
  public static final String REPLICATOIN_REPLICAS_LIST = "list";

  public static final String DATA_FLOW_TOPOLOGY = "dataFlowTopology";
  public static final String DATA_FLOW_TOPOLOGY_ROUTES = "routes";

  public static final String DEFAULT_DATA_FLOW_TOPOLOGIES_PUSHMODE = "defaultDataFlowTopologies_PushMode";
  public static final String DEFAULT_DATA_FLOW_TOPOLOGIES_PULLMODE = "defaultDataFlowTopologies_PullMode";

  public static final String DATA_FLOW_TOPOLOGY_PICKER = "dataFlowTopologyPicker";
  public static final String DEFAULT_DATA_FLOW_TOPOLOGY_PICKER_CLASS =
      DataFlowTopologyPickerByHadoopFsSource.class.getCanonicalName();

  public static final String END_POINT_FACTORY_CLASS = "endPointFactoryClass";
  public static final String DEFAULT_END_POINT_FACTORY_CLASS = HadoopFsEndPointFactory.class.getCanonicalName();

  public static final ClassAliasResolver<EndPointFactory> endPointFactoryResolver =
      new ClassAliasResolver<>(EndPointFactory.class);

  @Getter
  private final ReplicationCopyMode copyMode;

  @Getter
  private final ReplicationMetaData metaData;

  @Getter
  private final EndPoint source;

  @Getter
  private final List<EndPoint> replicas;

  @Getter
  private final DataFlowTopology dataFlowToplogy;

  public static ReplicationConfiguration buildFromConfig(Config config)
      throws InstantiationException, IllegalAccessException, ClassNotFoundException {
    Preconditions.checkArgument(config != null, "can not build ReplicationConfig from null");

    return new Builder().withReplicationMetaData(ReplicationMetaData.buildMetaData(config))
        .withReplicationCopyMode(ReplicationCopyMode.getReplicationCopyMode(config)).withReplicationSource(config)
        .withReplicationReplica(config).withDefaultDataFlowTopologyConfig_PullMode(config)
        .withDefaultDataFlowTopologyConfig_PushMode(config).withDataFlowTopologyConfig(config).build();
  }

  private ReplicationConfiguration(Builder builder) {
    this.metaData = builder.metaData;
    this.source = builder.source;
    this.replicas = builder.replicas;
    this.copyMode = builder.copyMode;
    this.dataFlowToplogy = builder.dataFlowTopology;
  }

  private static class Builder {
    public static final ClassAliasResolver<DataFlowTopologyPickerBySource> dataFlowTopologyPickerResolver =
        new ClassAliasResolver<>(DataFlowTopologyPickerBySource.class);

    private ReplicationMetaData metaData;

    private EndPoint source;

    private List<EndPoint> replicas = new ArrayList<EndPoint>();

    private ReplicationCopyMode copyMode;

    private Config dataFlowTopologyConfig;

    private Optional<Config> defaultDataFlowTopology_PushModeConfig;

    private Optional<Config> defaultDataFlowTopology_PullModeConfig;

    private DataFlowTopology dataFlowTopology = new DataFlowTopology();

    public Builder withReplicationMetaData(ReplicationMetaData metaData) {
      this.metaData = metaData;
      return this;
    }

    public Builder withReplicationSource(Config config)
        throws InstantiationException, IllegalAccessException, ClassNotFoundException {
      Preconditions.checkArgument(config.hasPath(REPLICATION_SOURCE),
          "missing required config entery " + REPLICATION_SOURCE);

      Config sourceConfig = config.getConfig(REPLICATION_SOURCE);
      String endPointFactory = sourceConfig.hasPath(END_POINT_FACTORY_CLASS)
          ? sourceConfig.getString(END_POINT_FACTORY_CLASS) : DEFAULT_END_POINT_FACTORY_CLASS;

      EndPointFactory factory = endPointFactoryResolver.resolveClass(endPointFactory).newInstance();
      this.source = factory.buildSource(sourceConfig);
      return this;
    }

    public Builder withReplicationReplica(Config config)
        throws InstantiationException, IllegalAccessException, ClassNotFoundException {
      Preconditions.checkArgument(config.hasPath(REPLICATION_REPLICAS),
          "missing required config entery " + REPLICATION_REPLICAS);

      Config replicasConfig = config.getConfig(REPLICATION_REPLICAS);
      Preconditions.checkArgument(replicasConfig.hasPath(REPLICATOIN_REPLICAS_LIST),
          "missing required config entery " + REPLICATOIN_REPLICAS_LIST);
      List<String> replicaNames = replicasConfig.getStringList(REPLICATOIN_REPLICAS_LIST);

      for (String replicaName : replicaNames) {
        Preconditions.checkArgument(replicasConfig.hasPath(replicaName), "missing replica name " + replicaName);
        Config subConfig = replicasConfig.getConfig(replicaName);

        // each replica could have own EndPointFactory resolver 
        String endPointFactory = subConfig.hasPath(END_POINT_FACTORY_CLASS)
            ? subConfig.getString(END_POINT_FACTORY_CLASS) : DEFAULT_END_POINT_FACTORY_CLASS;
        EndPointFactory factory = endPointFactoryResolver.resolveClass(endPointFactory).newInstance();
        this.replicas.add(factory.buildReplica(subConfig, replicaName));
      }
      return this;
    }

    public Builder withDataFlowTopologyConfig(Config config) {
      Preconditions.checkArgument(config.hasPath(DATA_FLOW_TOPOLOGY),
          "missing required config entery " + DATA_FLOW_TOPOLOGY);

      this.dataFlowTopologyConfig = config.getConfig(DATA_FLOW_TOPOLOGY);
      return this;
    }

    public Builder withDefaultDataFlowTopologyConfig_PushMode(Config config) {
      if (config.hasPath(DEFAULT_DATA_FLOW_TOPOLOGIES_PUSHMODE)) {
        this.defaultDataFlowTopology_PushModeConfig =
            Optional.of(config.getConfig(DEFAULT_DATA_FLOW_TOPOLOGIES_PUSHMODE));
      } else {
        this.defaultDataFlowTopology_PushModeConfig = Optional.absent();
      }
      return this;
    }

    public Builder withDefaultDataFlowTopologyConfig_PullMode(Config config) {
      if (config.hasPath(DEFAULT_DATA_FLOW_TOPOLOGIES_PULLMODE)) {
        this.defaultDataFlowTopology_PullModeConfig =
            Optional.of(config.getConfig(DEFAULT_DATA_FLOW_TOPOLOGIES_PULLMODE));
      } else {
        this.defaultDataFlowTopology_PullModeConfig = Optional.absent();
      }
      return this;
    }

    public Builder withReplicationCopyMode(ReplicationCopyMode copyMode) {
      this.copyMode = copyMode;
      return this;
    }

    private void constructDataFlowTopology()
        throws InstantiationException, IllegalAccessException, ClassNotFoundException {
      if (this.dataFlowTopologyConfig.hasPath(DATA_FLOW_TOPOLOGY_ROUTES)) {
        Config routesConfig = dataFlowTopologyConfig.getConfig(DATA_FLOW_TOPOLOGY_ROUTES);
        constructDataFlowTopologyWithConfig(routesConfig);
        return;
      }

      // topology not specified in literal, need to pick one topology from the defaults
      Preconditions.checkArgument(this.dataFlowTopologyConfig.hasPath(DATA_FLOW_TOPOLOGY_PICKER),
          "missing required config entry " + DATA_FLOW_TOPOLOGY_PICKER);
      String topologyPickerStr = this.dataFlowTopologyConfig.getString(DATA_FLOW_TOPOLOGY_PICKER);
      DataFlowTopologyPickerBySource picker =
          dataFlowTopologyPickerResolver.resolveClass(topologyPickerStr).newInstance();

      if (this.copyMode == ReplicationCopyMode.PULL) {
        Preconditions.checkArgument(this.defaultDataFlowTopology_PullModeConfig.isPresent(),
            "No topology to pick in pull mode");
        Config preferredTopology =
            picker.getPreferredRoutes(this.defaultDataFlowTopology_PullModeConfig.get(), this.source);
        Config routesConfig = preferredTopology.getConfig(DATA_FLOW_TOPOLOGY_ROUTES);

        constructDataFlowTopologyWithConfig(routesConfig);
      } else {
        Preconditions.checkArgument(this.defaultDataFlowTopology_PushModeConfig.isPresent(),
            "No topology to pick in push mode");
        Config preferredTopology =
            picker.getPreferredRoutes(this.defaultDataFlowTopology_PushModeConfig.get(), this.source);
        Config routesConfig = preferredTopology.getConfig(DATA_FLOW_TOPOLOGY_ROUTES);

        constructDataFlowTopologyWithConfig(routesConfig);
      }
    }

    private void constructDataFlowTopologyWithConfig(Config routesConfig) {
      Preconditions.checkArgument(routesConfig != null && !routesConfig.isEmpty(),
          "Can not build topology without empty config");
      Preconditions.checkArgument(this.source != null, "Can not build topology without source");
      Preconditions.checkArgument(this.replicas.size() != 0, "Can not build topology without replicas");

      final Map<String, EndPoint> validEndPoints = new HashMap<>();
      validEndPoints.put(this.source.getEndPointName(), this.source);
      for (EndPoint p : this.replicas) {
        validEndPoints.put(p.getEndPointName(), p);
      }

      // PULL mode
      if (this.copyMode == ReplicationCopyMode.PULL) {
        // copy to original source will be ignored

        for (final EndPoint replica : this.replicas) {
          Preconditions.checkArgument(routesConfig.hasPath(replica.getEndPointName()),
              "Can not find the pull flow for replia " + replica.getEndPointName());

          List<String> copyFromStringsRaw = routesConfig.getStringList(replica.getEndPointName());
          // filter out invalid entries
          List<String> copyFromStrings = new ArrayList<>();
          for(String s: copyFromStringsRaw){
            if(validEndPoints.containsKey(s)){
              copyFromStrings.add(s);
            }
          }
          
          List<CopyRoute> copyPairs = Lists.transform(copyFromStrings, new Function<String, CopyRoute>() {
            @Override
            public CopyRoute apply(String t) {
              // create CopyPair in Pull mode
              return new CopyRoute(validEndPoints.get(t), replica);
            }
          });

          DataFlowTopology.DataFlowPath dataFlowPath = new DataFlowTopology.DataFlowPath(copyPairs);
          this.dataFlowTopology.addDataFlowPath(dataFlowPath);
        }
      }
      // PUSH mode
      else {
        Set<String> currentCopyTo = new HashSet<>();
        for (final Map.Entry<String, EndPoint> valid : validEndPoints.entrySet()) {
          if (routesConfig.hasPath(valid.getKey())) {
            List<String> copyToStringsRaw = routesConfig.getStringList(valid.getKey());
            List<String> copyToStrings = new ArrayList<>();
            
            for(String s: copyToStringsRaw){
              if(!s.equals(this.source.getEndPointName()) &&validEndPoints.containsKey(s)){
                copyToStrings.add(s);
              }
            }
            
            // filter out invalid entries
            for (String s : copyToStrings) {
              Preconditions.checkArgument(!currentCopyTo.contains(s),
                  "In Push mode, can not have multiple copies to " + s);
            }
            currentCopyTo.addAll(copyToStrings);

            List<CopyRoute> copyPairs = Lists.transform(copyToStrings, new Function<String, CopyRoute>() {
              @Override
              public CopyRoute apply(String t) {
                // create CopyPair in Push mode
                return new CopyRoute(valid.getValue(), validEndPoints.get(t));
              }
            });

            DataFlowTopology.DataFlowPath dataFlowPath = new DataFlowTopology.DataFlowPath(copyPairs);
            this.dataFlowTopology.addDataFlowPath(dataFlowPath);
          }
        }

        Preconditions.checkArgument(currentCopyTo.size() == this.replicas.size(),
            "Not all replicas have valid data flow in push mode");
      }
    }

    public ReplicationConfiguration build()
        throws InstantiationException, IllegalAccessException, ClassNotFoundException {
      constructDataFlowTopology();
      return new ReplicationConfiguration(this);
    }
  }
}
