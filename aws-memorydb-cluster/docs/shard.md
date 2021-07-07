# AWS::MemoryDB::Cluster Shard

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "<a href="#name" title="Name">Name</a>" : <i>String</i>,
    "<a href="#status" title="Status">Status</a>" : <i>String</i>,
    "<a href="#slots" title="Slots">Slots</a>" : <i>String</i>,
    "<a href="#nodes" title="Nodes">Nodes</a>" : <i>[ <a href="node.md">Node</a>, ... ]</i>,
    "<a href="#numnodes" title="NumNodes">NumNodes</a>" : <i>Integer</i>
}
</pre>

### YAML

<pre>
<a href="#name" title="Name">Name</a>: <i>String</i>
<a href="#status" title="Status">Status</a>: <i>String</i>
<a href="#slots" title="Slots">Slots</a>: <i>String</i>
<a href="#nodes" title="Nodes">Nodes</a>: <i>
      - <a href="node.md">Node</a></i>
<a href="#numnodes" title="NumNodes">NumNodes</a>: <i>Integer</i>
</pre>

## Properties

#### Name

The name of the shard.

_Required_: No

_Type_: String

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Status

The status of the shard.

_Required_: No

_Type_: String

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Slots

number of slots in the shard.

_Required_: No

_Type_: String

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Nodes

Nodes in the shard.

_Required_: No

_Type_: List of <a href="node.md">Node</a>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### NumNodes

number of nodes in the shard.

_Required_: No

_Type_: Integer

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

