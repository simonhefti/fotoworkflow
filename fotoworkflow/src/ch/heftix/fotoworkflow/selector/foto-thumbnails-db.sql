-- Copyright (C) 2007-2013 by Simon Hefti. All rights reserved.
--
-- Licensed under the EPL 1.0 (Eclipse Public License).
-- (see http://www.eclipse.org/legal/epl-v10.html)
--
-- Software distributed under the License is distributed on an "AS IS"
-- basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
--
-- Initial Developer: Simon Hefti

-- thumbnail cache
create table thumbnail (
  fotoid int,
  path text,
  image blob,
  height integer,
  mimetype text
);

-- list of known fotos
create virtual table foto using fts4 (
  fotoid int primary key,
  path text not null, -- path on file system, e.g. /foo/bar/1.jpg
  mimetype text, -- mime type of this file, e.g. image/jpeg
  creationdate text, -- format yyyy-MM-dd'T'HHmm
  year int,
  month int,
  day int,
  hour int,
  minute int,
  w int, -- width
  h int, -- height
  make text, -- camera make, e.g. Nikon
  model text, -- camera model, e.g. Sinarback 54 M, Hasselblad
  geo_long text, -- longitude in degrees, e.g. 10.0 (west is negative)
  geo_lat text, -- latitude in degrees, e.g. 40.0 (south is negative)
  orientation text,  -- orientation hint, e.g. right side (Rotate 180)
  category text, -- likes, e.g. best-of
  note text, -- a description of the image
  isMissing int, -- flag
  isPrivate int -- flag
);

-- migration
-- create table fotobck as select * from foto;
-- drop table foto;
-- create new table (see above)
-- insert into foto select abs(random() % 100000), path, mimetype, creationdate, year, month, day, hour, minute, w, h, make, model, geo_long, geo_lat, orientation, category, note, isMissing, 0 from fotobck;
-- drop table fotobck;


-- distance between fotos
create table distance (
  p1 text, -- path 1
  p2 text,  -- path 2
  d int
);

-- from fotos, create a story
create table story (
  storyid int, -- identifies story
  name text, -- name of this story
  caption text, -- description
  titlefoto text, -- title foto
  category text -- story can have rating, e.g. best-of
);

-- fotos which belong to a story
create table storyfotos (
  storyid int, -- which story
  fotoid int,  -- which foto
  sorting int, -- order of fotos within story
  caption text -- description
);

-- record user events for analysis
create table event (
  stamp timestamp,
  type text, -- event type (i.e. web command name)
  arg1 text, -- describing type in more detail, e.g. category for type store
  arg2 text, -- describing type in more detail, e.g. best-of as stored category
  user text,
  fotoid int,
  path text, -- PK of foto
  uri text, -- ressource
  qs text -- query string
);

-- migration
-- create table eventbck as select * from event;
-- drop table event;
-- create (see above)
-- insert into event select stamp, type, null, null, user, -1, path, uri, qs from eventbck;
-- drop table eventbck;


-- used to keep track of current schema version
create table conf (
  k text, -- key
  v text  -- value
);

insert into conf (k,v) values ('version',1);
insert into conf (k,v) values ('importPattern','/@{CreationDate: yyyy}/@{Model}/@{CreationDate: yyyy-MM}/@{CreationDate: yyyy-MM-dd''T''HHmm}_@{Filename}@{Unique}.@{Extension}');

-- poor mans sequences  
create table sequence(
  name text primary key, 
  val integer
);

insert into sequence values ('story',     1000);
insert into sequence values ('foto',    100000);
