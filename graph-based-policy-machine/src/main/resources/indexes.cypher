DROP INDEX ControlRelation IF EXISTS;
DROP INDEX AccessRelation IF EXISTS;
DROP INDEX OwnsRelation IF EXISTS;

DROP INDEX ENodes IF EXISTS;
DROP INDEX ONodes IF EXISTS;
DROP INDEX OANodes IF EXISTS;
DROP INDEX INodes IF EXISTS;

DROP INDEX EEntityCLass IF EXISTS;

CREATE INDEX ENodes
FOR (e:E)
ON (e.id);

CREATE INDEX ONodes
FOR (o:O)
ON (o.id);

CREATE INDEX OANodes
FOR (oa:OA)
ON (oa.id);

CREATE INDEX INodes
FOR (i:I)
ON (i.id);


CREATE INDEX EEntityCLass FOR (e:E)
ON (e.entityClass);

CREATE INDEX ControlRelation
FOR ()-[r:relation]-()
ON (r.control);


CREATE  INDEX AccessRelation
FOR ()-[r:relation]-()
ON (r.access);


CREATE  INDEX OwnsRelation
FOR ()-[r:relation]-()
ON (r.owns);


//replacing string to boolean
MATCH (i:I)-[rel:relation {access: '1'}]->(oa:OA)
SET rel.access = 1;

MATCH (i:I)-[rel:relation {control: '1'}]->(oa:OA)
SET rel.control = 1;

MATCH (i:I)-[rel:relation {owns: '1'}]->(oa:OA)
SET rel.owns = 1;

MATCH (i:I)-[rel:relation {access: '0'}]->(oa:OA)
SET rel.access = 0;

MATCH (i:I)-[rel:relation {control: '0'}]->(oa:OA)
SET rel.control = 0;

MATCH (i:I)-[rel:relation {owns: '0'}]->(oa:OA)
SET rel.owns = 0;


