# AWS::MemoryDB::SubnetGroup Tag

A key-value pair to associate with a resource.

## Syntax

To declare this entity in your AWS CloudFormation template, use the following syntax:

### JSON

<pre>
{
    "<a href="#key" title="Key">Key</a>" : <i>String</i>,
    "<a href="#value" title="Value">Value</a>" : <i>String</i>
}
</pre>

### YAML

<pre>
<a href="#key" title="Key">Key</a>: <i>String</i>
<a href="#value" title="Value">Value</a>: <i>String</i>
</pre>

## Properties

#### Key

The key for the tag. May not be null.

_Required_: Yes

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>128</code>

_Pattern_: <code>^(?!aws:)(?!memorydb:)[a-zA-Z0-9 _\.\/=+:\-@]{1,128}$</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)

#### Value

The tag's value. May be null.

_Required_: Yes

_Type_: String

_Minimum_: <code>1</code>

_Maximum_: <code>256</code>

_Pattern_: <code>^(?!aws:)(?!memorydb:)[a-zA-Z0-9 _\.\/=+:\-@]{1,256}$</code>

_Update requires_: [No interruption](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/using-cfn-updating-stacks-update-behaviors.html#update-no-interrupt)
