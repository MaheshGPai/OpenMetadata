/*
 *  Copyright 2022 Collate
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import { Button, Col, Modal, Row, Space, Switch, Table, Tooltip } from 'antd';
import { ColumnsType } from 'antd/lib/table';
import { AxiosError } from 'axios';
import { isEmpty, isUndefined } from 'lodash';
import React, { FC, useMemo, useState } from 'react';
import { useHistory } from 'react-router-dom';
import { updateUser } from '../../axiosAPIs/userAPI';
import { PAGE_SIZE_MEDIUM, ROUTES } from '../../constants/constants';
import { NO_PERMISSION_FOR_ACTION } from '../../constants/HelperTextUtil';
import { CreateUser } from '../../generated/api/teams/createUser';
import { Operation } from '../../generated/entity/policies/policy';
import { User } from '../../generated/entity/teams/user';
import { Paging } from '../../generated/type/paging';
import jsonData from '../../jsons/en';
import {
  commonUserDetailColumns,
  getEntityName,
} from '../../utils/CommonUtils';
import { checkPermission } from '../../utils/PermissionsUtils';
import SVGIcons, { Icons } from '../../utils/SvgUtils';
import { showErrorToast, showSuccessToast } from '../../utils/ToastUtils';
import DeleteWidgetModal from '../common/DeleteWidget/DeleteWidgetModal';
import ErrorPlaceHolder from '../common/error-with-placeholder/ErrorPlaceHolder';
import NextPrevious from '../common/next-previous/NextPrevious';
import Searchbar from '../common/searchbar/Searchbar';
import Loader from '../Loader/Loader';
import { usePermissionProvider } from '../PermissionProvider/PermissionProvider';
import { ResourceEntity } from '../PermissionProvider/PermissionProvider.interface';
import './usersList.less';

interface UserListV1Props {
  data: User[];
  paging: Paging;
  searchTerm: string;
  currentPage: number;
  isDataLoading: boolean;
  showDeletedUser: boolean;
  onPagingChange: (cursorValue: string | number, activePage?: number) => void;
  onShowDeletedUserChange: (value: boolean) => void;
  onSearch: (text: string) => void;
  afterDeleteAction: () => void;
}

const UserListV1: FC<UserListV1Props> = ({
  data,
  paging,
  searchTerm,
  currentPage,
  isDataLoading,
  showDeletedUser,
  onSearch,
  onShowDeletedUserChange,
  onPagingChange,
  afterDeleteAction,
}) => {
  const { permissions } = usePermissionProvider();
  const history = useHistory();
  const [selectedUser, setSelectedUser] = useState<User>();
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [showReactiveModal, setShowReactiveModal] = useState(false);
  const showRestore = showDeletedUser && !isDataLoading;

  const createPermission = useMemo(
    () => checkPermission(Operation.Create, ResourceEntity.USER, permissions),
    [permissions]
  );

  const deletePermission = useMemo(
    () => checkPermission(Operation.Delete, ResourceEntity.USER, permissions),
    [permissions]
  );

  const handleAddNewUser = () => {
    history.push(ROUTES.CREATE_USER);
  };

  const handleReactiveUser = async () => {
    if (isUndefined(selectedUser)) {
      return;
    }

    const updatedUserData: CreateUser = {
      description: selectedUser.description,
      displayName: selectedUser.displayName,
      email: selectedUser.email,
      isAdmin: selectedUser.isAdmin,
      name: selectedUser.name,
      profile: selectedUser.profile,
      roles: selectedUser.roles?.map((role) => role.id),
      teams: selectedUser.teams?.map((team) => team.id),
    };

    try {
      const { data } = await updateUser(updatedUserData);
      if (data) {
        afterDeleteAction();
        showSuccessToast(
          jsonData['api-success-messages']['user-restored-success']
        );
      } else {
        throw jsonData['api-error-messages']['update-user-error'];
      }
    } catch (error) {
      showErrorToast(
        error as AxiosError,
        jsonData['api-error-messages']['update-user-error']
      );
    }
    setSelectedUser(undefined);
    setShowReactiveModal(false);
  };

  const columns: ColumnsType<User> = useMemo(() => {
    return [
      ...commonUserDetailColumns,
      {
        title: 'Actions',
        dataIndex: 'actions',
        key: 'actions',
        width: 90,
        render: (_, record) => (
          <Space
            align="center"
            className="tw-w-full tw-justify-center action-icons"
            size={8}>
            {showRestore && (
              <Tooltip placement="bottom" title="Restore">
                <Button
                  icon={
                    <SVGIcons
                      alt="Restore"
                      className="tw-w-4 tw-mb-2.5"
                      data-testid={`restore-user-btn-${
                        record.displayName || record.name
                      }`}
                      icon={Icons.RESTORE}
                    />
                  }
                  type="text"
                  onClick={() => {
                    setSelectedUser(record);
                    setShowReactiveModal(true);
                  }}
                />
              </Tooltip>
            )}
            <Tooltip
              placement="bottom"
              title={deletePermission ? 'Delete' : NO_PERMISSION_FOR_ACTION}>
              <Button
                disabled={!deletePermission}
                icon={
                  <SVGIcons
                    alt="Delete"
                    className="tw-w-4 tw-mb-2.5"
                    data-testid={`delete-user-btn-${
                      record.displayName || record.name
                    }`}
                    icon={Icons.DELETE}
                  />
                }
                type="text"
                onClick={() => {
                  setSelectedUser(record);
                  setShowDeleteModal(true);
                }}
              />
            </Tooltip>
          </Space>
        ),
      },
    ];
  }, [showRestore]);

  const fetchErrorPlaceHolder = useMemo(
    () => (type: string) => {
      return (
        <Row>
          <Col className="w-full tw-flex tw-justify-end">
            <span>
              <Switch
                checked={showDeletedUser}
                size="small"
                onClick={onShowDeletedUserChange}
              />
              <span className="tw-ml-2">Deleted Users</span>
            </span>
          </Col>
          <Col span={24}>
            <ErrorPlaceHolder
              buttons={
                <Button
                  ghost
                  data-testid="add-user"
                  disabled={!createPermission}
                  type="primary"
                  onClick={handleAddNewUser}>
                  Add User
                </Button>
              }
              heading="User"
              type={type}
            />
          </Col>
        </Row>
      );
    },
    []
  );

  if (isEmpty(data) && !showDeletedUser && !isDataLoading && !searchTerm) {
    return fetchErrorPlaceHolder('ADD_DATA');
  }

  return (
    <Row className="user-listing" gutter={[16, 16]}>
      <Col span={8}>
        <Searchbar
          removeMargin
          placeholder="Search for user..."
          searchValue={searchTerm}
          typingInterval={500}
          onSearch={onSearch}
        />
      </Col>
      <Col span={16}>
        <Space align="center" className="tw-w-full tw-justify-end" size={16}>
          <span>
            <Switch
              checked={showDeletedUser}
              onClick={onShowDeletedUserChange}
            />
            <span className="tw-ml-2">Deleted Users</span>
          </span>
          <Tooltip
            title={createPermission ? 'Add User' : NO_PERMISSION_FOR_ACTION}>
            <Button
              data-testid="add-user"
              disabled={!createPermission}
              type="primary"
              onClick={handleAddNewUser}>
              Add User
            </Button>
          </Tooltip>
        </Space>
      </Col>

      <Col span={24}>
        <Table
          className="user-list-table"
          columns={columns}
          dataSource={data}
          loading={{
            spinning: isDataLoading,
            indicator: <Loader size="small" />,
          }}
          pagination={false}
          size="small"
        />
      </Col>
      <Col span={24}>
        {paging.total > PAGE_SIZE_MEDIUM && (
          <NextPrevious
            currentPage={currentPage}
            isNumberBased={Boolean(searchTerm)}
            pageSize={PAGE_SIZE_MEDIUM}
            paging={paging}
            pagingHandler={onPagingChange}
            totalCount={paging.total}
          />
        )}
      </Col>

      <Modal
        cancelButtonProps={{
          type: 'link',
        }}
        className="reactive-modal"
        closable={false}
        okText="Restore"
        title="restore User"
        visible={showReactiveModal}
        onCancel={() => {
          setShowReactiveModal(false);
          setSelectedUser(undefined);
        }}
        onOk={handleReactiveUser}>
        <p>Are you sure you want to restore {getEntityName(selectedUser)}?</p>
      </Modal>

      <DeleteWidgetModal
        afterDeleteAction={afterDeleteAction}
        allowSoftDelete={!showDeletedUser}
        entityId={selectedUser?.id || ''}
        entityName={selectedUser?.name || ''}
        entityType="user"
        visible={showDeleteModal}
        onCancel={() => {
          setShowDeleteModal(false);
          setSelectedUser(undefined);
        }}
      />
    </Row>
  );
};

export default UserListV1;
